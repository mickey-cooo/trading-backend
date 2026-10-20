package trading.demo.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import trading.demo.exception.ConflictException;
import trading.demo.exception.ResourceNotFoundException;
import trading.demo.mapper.OrderMapper;
import trading.demo.model.dto.orderDto.OrderResponse;
import trading.demo.model.dto.orderDto.SubmitOrderRequest;
import trading.demo.model.dto.orderDto.TradeResponse;
import trading.demo.model.entity.AccountEntity;
import trading.demo.model.entity.ExecutionEntity;
import trading.demo.model.entity.MarketEntity;
import trading.demo.model.entity.OrderEntity;
import trading.demo.model.enums.BalanceBucket;
import trading.demo.model.enums.Common;
import trading.demo.model.enums.LedgerType;
import trading.demo.model.enums.OrderSide;
import trading.demo.model.enums.OrderStatus;
import trading.demo.repository.ExecutionRepository;
import trading.demo.repository.MarketRepository;
import trading.demo.repository.OrderRepository;
import trading.demo.repository.QuoteRepository;
import trading.demo.repository.UserRepository;
import trading.demo.service.LedgerService.Leg;

@Service
public class OrderServiceImpl implements OrderService {
	private final OrderRepository orderRepository;
	private final ExecutionRepository executionRepository;
	private final MarketRepository marketRepository;
	private final QuoteRepository quoteRepository;
	private final UserRepository userRepository;
	private final MarketService marketService;
	private final LedgerService ledgerService;
	private final OrderMapper orderMapper;
	private final TransactionTemplate transaction;

	public OrderServiceImpl(OrderRepository orderRepository, ExecutionRepository executionRepository,
			MarketRepository marketRepository, QuoteRepository quoteRepository, UserRepository userRepository,
			MarketService marketService, LedgerService ledgerService, OrderMapper orderMapper,
			PlatformTransactionManager transactionManager) {
		this.orderRepository = orderRepository;
		this.executionRepository = executionRepository;
		this.marketRepository = marketRepository;
		this.quoteRepository = quoteRepository;
		this.userRepository = userRepository;
		this.marketService = marketService;
		this.ledgerService = ledgerService;
		this.orderMapper = orderMapper;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	// not @Transactional: findOrOpen and systemAccount use their own transactions,
	// and an outer one would hold a
	// second connection while they wait for one
	@Override
	public OrderResponse submit(UUID userId, String idempotencyKey, SubmitOrderRequest request) {
		// first, so the hash below never sees an absurd number like 1E+2147483647
		marketService.requireValidQuantity(request.quantity());
		var pair = request.symbol().strip().toUpperCase(Locale.ROOT);
		var hash = AuthServiceImpl.hash(String.join("|", pair, request.side().name(), request.type().name(),
				request.quantity().stripTrailingZeros().toPlainString()));

		var earlier = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		if (earlier.isPresent()) {
			return replay(earlier.get(), hash);
		}
		var account = ledgerService.findOrOpen(userId);
		var system = ledgerService.systemAccount();
		try {
			return transaction.execute(status -> place(userId, account, system, idempotencyKey, hash, pair, request));
		} catch (DataIntegrityViolationException raced) {
			// a retry with the same key committed first
			return orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
					.map(order -> replay(order, hash)).orElseThrow(() -> raced);
		}
	}

	private OrderResponse place(UUID userId, AccountEntity account, AccountEntity system, String idempotencyKey,
			String hash, String pair, SubmitOrderRequest request) {
		// lock order: market first, then balances (inside ledgerService.post), the same
		// everywhere
		MarketEntity market = marketRepository.findByPairLockedForWrite(pair)
				.orElseThrow(() -> new ResourceNotFoundException("Market not found"));
		if (market.getStatus() != Common.ACTIVE) {
			throw new ConflictException("Market is closed");
		}
		var quote = quoteRepository.findFirstByMarketOrderByVersionDesc(market)
				.orElseThrow(() -> new ResourceNotFoundException("No quote has been published for this market yet"));

		var order = new OrderEntity();
		order.setUser(userRepository.getReferenceById(userId));
		order.setMarket(market);
		order.setSide(request.side());
		order.setType(request.type());
		order.setQuantity(request.quantity());
		order.setStatus(OrderStatus.FILLED);
		order.setIdempotencyKey(idempotencyKey);
		order.setRequestHash(hash);
		// flush now: a duplicate key fails here, before any money moves
		orderRepository.saveAndFlush(order);

		var execution = new ExecutionEntity();
		execution.setOrder(order);
		execution.setPrice(quote.getPrice());
		execution.setQuantity(request.quantity());
		execution.setQuoteVersion(quote.getVersion());
		executionRepository.save(execution);

		ledgerService.post("trade:" + order.getId(), LedgerType.TRADE,
				tradeLegs(account, system, market, request.side(), request.quantity(), quote.getPrice()));
		return orderMapper.toOrderResponse(order);
	}

	// the buyer pays quote asset and gets base asset; a sell is the same with the
	// signs flipped
	private static List<Leg> tradeLegs(AccountEntity account, AccountEntity system, MarketEntity market, OrderSide side,
			BigDecimal quantity, BigDecimal price) {
		var cost = quantity.multiply(price);
		var base = market.getBaseAsset();
		var quote = market.getQuoteAsset();
		var buy = side == OrderSide.BUY;
		return List.of(new Leg(account, quote, BalanceBucket.AVAILABLE, buy ? cost.negate() : cost),
				new Leg(account, base, BalanceBucket.AVAILABLE, buy ? quantity : quantity.negate()),
				new Leg(system, quote, BalanceBucket.AVAILABLE, buy ? cost : cost.negate()),
				new Leg(system, base, BalanceBucket.AVAILABLE, buy ? quantity.negate() : quantity));
	}

	private OrderResponse replay(OrderEntity order, String hash) {
		if (!order.getRequestHash().equals(hash)) {
			throw new ConflictException("Idempotency-Key was already used for a different request");
		}
		return orderMapper.toOrderResponse(order);
	}

	@Override
	public Page<OrderResponse> list(UUID userId, int page, int size) {
		return orderRepository.findByUserId(userId, newestFirst(page, size)).map(orderMapper::toOrderResponse);
	}

	@Override
	public OrderResponse get(UUID userId, UUID orderId) {
		return orderRepository.findByIdAndUserId(orderId, userId).map(orderMapper::toOrderResponse)
				.orElseThrow(() -> new ResourceNotFoundException("Order not found"));
	}

	@Override
	public Page<TradeResponse> trades(UUID userId, int page, int size) {
		return executionRepository.findByOrderUserId(userId, newestFirst(page, size)).map(orderMapper::toTradeResponse);
	}

	private static PageRequest newestFirst(int page, int size) {
		return PageRequest.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
	}
}
