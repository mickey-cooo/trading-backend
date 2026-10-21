package trading.demo.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import trading.demo.exception.ConflictException;
import trading.demo.exception.ResourceNotFoundException;
import trading.demo.exception.ValidationException;
import trading.demo.mapper.OrderMapper;
import trading.demo.model.dto.orderDto.OrderResponse;
import trading.demo.model.dto.orderDto.SubmitOrderRequest;
import trading.demo.model.dto.orderDto.TradeResponse;
import trading.demo.model.entity.AccountEntity;
import trading.demo.model.entity.AssetEntity;
import trading.demo.model.entity.TradeEntity;
import trading.demo.model.entity.MarketEntity;
import trading.demo.model.entity.OrderEntity;
import trading.demo.model.entity.QuoteEntity;
import trading.demo.model.entity.UserEntity;
import trading.demo.model.enums.BalanceBucket;
import trading.demo.model.enums.Common;
import trading.demo.model.enums.LedgerType;
import trading.demo.model.enums.OrderSide;
import trading.demo.model.enums.OrderStatus;
import trading.demo.model.enums.OrderType;
import trading.demo.repository.TradeRepository;
import trading.demo.repository.MarketRepository;
import trading.demo.repository.OrderRepository;
import trading.demo.repository.QuoteRepository;
import trading.demo.repository.UserRepository;
import trading.demo.service.LedgerService.Leg;

// Lock order: market first, then balances (in ledgerService.post). Submit, cancel and the matcher all follow it.
@Service
public class OrderServiceImpl implements OrderService {
	private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

	private final OrderRepository orderRepository;
	private final TradeRepository tradeRepository;
	private final MarketRepository marketRepository;
	private final QuoteRepository quoteRepository;
	private final UserRepository userRepository;
	private final MarketService marketService;
	private final MarketHours marketHours;
	private final LedgerService ledgerService;
	private final OrderMapper orderMapper;
	private final TransactionTemplate transaction;

	public OrderServiceImpl(OrderRepository orderRepository, TradeRepository tradeRepository,
			MarketRepository marketRepository, QuoteRepository quoteRepository, UserRepository userRepository,
			MarketService marketService, MarketHours marketHours, LedgerService ledgerService, OrderMapper orderMapper,
			PlatformTransactionManager transactionManager) {
		this.orderRepository = orderRepository;
		this.tradeRepository = tradeRepository;
		this.marketRepository = marketRepository;
		this.quoteRepository = quoteRepository;
		this.userRepository = userRepository;
		this.marketService = marketService;
		this.marketHours = marketHours;
		this.ledgerService = ledgerService;
		this.orderMapper = orderMapper;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	// not @Transactional: findOrOpen and systemAccount use their own transactions; an outer one would hold a second connection
	@Override
	public OrderResponse submit(UUID userId, String idempotencyKey, SubmitOrderRequest request) {
		// first, so the hash below never sees an absurd number like 1E+2147483647
		marketService.requireValidQuantity(request.quantity());
		if (request.type() == OrderType.LIMIT) {
			marketService.requireValidPrice(request.limitPrice());
		} else if (request.limitPrice() != null) {
			throw new ValidationException("limitPrice is only for LIMIT orders");
		}
		var pair = request.symbol().strip().toUpperCase(Locale.ROOT);
		var hash = hashOf(pair, request);

		var earlier = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		if (earlier.isPresent()) {
			return replay(earlier.get(), hash);
		}
		// after the replay lookup so a retry of an accepted order still gets its answer; before any lock or account work
		marketHours.requireOpenAt(Instant.now());
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
		var market = lockMarket(pair);
		if (market.getStatus() != Common.ACTIVE) {
			throw new ConflictException("Market is closed");
		}
		// an admin may have suspended the user after the request passed the login check
		if (!userRepository.findById(userId).map(UserEntity::isEnabled).orElse(false)) {
			throw new ConflictException("Account is suspended");
		}
		var quote = quoteRepository.findFirstByMarketOrderByVersionDesc(market);
		var isMarketOrder = request.type() == OrderType.MARKET;
		if (isMarketOrder && quote.isEmpty()) {
			throw new ResourceNotFoundException("No quote has been published for this market yet");
		}

		var order = new OrderEntity();
		order.setUser(userRepository.getReferenceById(userId));
		order.setMarket(market);
		order.setSide(request.side());
		order.setType(request.type());
		order.setQuantity(request.quantity());
		order.setLimitPrice(request.limitPrice());
		order.setStatus(OrderStatus.OPEN);
		order.setIdempotencyKey(idempotencyKey);
		order.setRequestHash(hash);
		// flush now: a duplicate key fails here, before any money moves
		orderRepository.saveAndFlush(order);

		if (isMarketOrder) {
			fill(order, market, quote.get(), account, system);
		} else {
			ledgerService.post("reserve:" + order.getId(), LedgerType.RESERVE, moveReserve(account, order, true));
			// checked once now; after this the matching job watches the order
			quote.filter(current -> hasReachedLimit(order, current))
					.ifPresent(current -> fill(order, market, current, account, system));
		}
		return orderMapper.toOrderResponse(order);
	}

	@Override
	public OrderResponse cancel(UUID userId, UUID orderId) {
		// find the market first: it has to be locked before anything else
		var pair = orderRepository.findByIdAndUserId(orderId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Order not found")).getMarket().getPair();
		var account = ledgerService.findOrOpen(userId);
		return transaction.execute(status -> {
			// a closed market can still be cancelled in, so the status is not checked here
			lockMarket(pair);
			// read again after the lock: a fill may have changed the order since the read above
			var order = orderRepository.findByIdAndUserId(orderId, userId)
					.orElseThrow(() -> new ResourceNotFoundException("Order not found"));
			if (order.getStatus() == OrderStatus.FILLED) {
				throw new ConflictException("Order is already filled");
			}
			if (order.getStatus() == OrderStatus.OPEN) {
				order.setStatus(OrderStatus.CANCELLED);
				ledgerService.post("release:" + order.getId(), LedgerType.RELEASE, moveReserve(account, order, false));
			}
			// already CANCELLED: return it as it is, nothing is released twice
			return orderMapper.toOrderResponse(order);
		});
	}

	@Override
	public int cancelOpenOrders(UUID userId) {
		return transaction.execute(status -> {
			var pairs = orderRepository.findOpenPairsByUserId(userId);
			if (pairs.isEmpty()) {
				return 0;
			}
			// the account already exists: these orders could not be placed without it
			var account = ledgerService.findOrOpen(userId);
			// markets first, in sorted order, then the balances inside post()
			pairs.forEach(this::lockMarket);
			// read after the locks: a fill may have changed an order since the first read
			var openOrders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.OPEN);
			for (var order : openOrders) {
				order.setStatus(OrderStatus.CANCELLED);
				// same reference as cancel(), so a reserve is never released twice
				ledgerService.post("release:" + order.getId(), LedgerType.RELEASE, moveReserve(account, order, false));
			}
			return openOrders.size();
		});
	}

	// shortcut: reads every open order each second; add a price filter in the query when there are many
	@Override
	public void matchOpenOrders() {
		// outside trading hours nothing fills; one check per tick, before any query
		if (!marketHours.isOpenAt(Instant.now())) {
			return;
		}
		var openOrderIds = orderRepository.findOpenOrderIds();
		if (openOrderIds.isEmpty()) {
			return;
		}
		var system = ledgerService.systemAccount();
		for (var orderId : openOrderIds) {
			try {
				matchOne(orderId, system);
			} catch (RuntimeException e) {
				// one bad order must not stop the others
				log.warn("Could not match order {}", orderId, e);
			}
		}
	}

	private void matchOne(UUID orderId, AccountEntity system) {
		var found = orderRepository.findWithMarketById(orderId);
		if (found.isEmpty() || found.get().getStatus() != OrderStatus.OPEN) {
			return;
		}
		// a suspended market has no fills; skipping here keeps its lock free
		if (found.get().getMarket().getStatus() != Common.ACTIVE) {
			return;
		}
		var pair = found.get().getMarket().getPair();
		// cheap check without the lock: most open orders have not reached their price and must not block the market every second
		var latestQuote = quoteRepository.findFirstByMarketOrderByVersionDesc(found.get().getMarket());
		if (latestQuote.isEmpty() || !hasReachedLimit(found.get(), latestQuote.get())) {
			return;
		}
		var account = ledgerService.findOrOpen(found.get().getUser().getId());
		transaction.executeWithoutResult(status -> {
			var market = lockMarket(pair);
			// read again after the lock: the user may have cancelled in the meantime
			var order = orderRepository.findWithMarketById(orderId).orElseThrow();
			// a suspended user's order waits, with its reserve, until they are active again
			if (order.getStatus() != OrderStatus.OPEN || market.getStatus() != Common.ACTIVE
					|| !order.getUser().isEnabled()) {
				return;
			}
			quoteRepository.findFirstByMarketOrderByVersionDesc(market)
					.filter(current -> hasReachedLimit(order, current))
					.ifPresent(current -> fill(order, market, current, account, system));
		});
	}

	// Fills the whole order at the current quote, not at the limit price.
	private void fill(OrderEntity order, MarketEntity market, QuoteEntity quote, AccountEntity account,
			AccountEntity system) {
		order.setStatus(OrderStatus.FILLED);

		var trade = new TradeEntity();
		trade.setOrder(order);
		trade.setPrice(quote.getPrice());
		trade.setQuantity(order.getQuantity());
		trade.setQuoteVersion(quote.getVersion());
		tradeRepository.save(trade);

		var isLimitOrder = order.getType() == OrderType.LIMIT;
		var cost = order.getQuantity().multiply(quote.getPrice());
		ledgerService.post("trade:" + order.getId(), LedgerType.TRADE,
				tradeLegs(account, system, market, order, cost, isLimitOrder));

		// a buy that filled cheaper than its limit gets the unused part of its reserve back
		if (isLimitOrder && order.getSide() == OrderSide.BUY) {
			var unused = reservedAmount(order).subtract(cost);
			if (unused.signum() > 0) {
				ledgerService.post("refund:" + order.getId(), LedgerType.RELEASE,
						moveBetweenBuckets(account, market.getQuoteAsset(), unused, false));
			}
		}
	}

	// A buy fills at this price or lower, a sell at this price or higher.
	private static boolean hasReachedLimit(OrderEntity order, QuoteEntity quote) {
		var comparison = quote.getPrice().compareTo(order.getLimitPrice());
		return order.getSide() == OrderSide.BUY ? comparison <= 0 : comparison >= 0;
	}

	// Buyer pays the quote asset and gets the base asset (sell flips the signs); limit pays from reserved, market from available.
	private static List<Leg> tradeLegs(AccountEntity account, AccountEntity system, MarketEntity market,
			OrderEntity order, BigDecimal cost, boolean payFromReserved) {
		var quantity = order.getQuantity();
		var payingBucket = payFromReserved ? BalanceBucket.RESERVED : BalanceBucket.AVAILABLE;
		var baseAsset = market.getBaseAsset();
		var quoteAsset = market.getQuoteAsset();
		if (order.getSide() == OrderSide.BUY) {
			return List.of(new Leg(account, quoteAsset, payingBucket, cost.negate()),
					new Leg(account, baseAsset, BalanceBucket.AVAILABLE, quantity),
					new Leg(system, quoteAsset, BalanceBucket.AVAILABLE, cost),
					new Leg(system, baseAsset, BalanceBucket.AVAILABLE, quantity.negate()));
		}
		return List.of(new Leg(account, baseAsset, payingBucket, quantity.negate()),
				new Leg(account, quoteAsset, BalanceBucket.AVAILABLE, cost),
				new Leg(system, baseAsset, BalanceBucket.AVAILABLE, quantity),
				new Leg(system, quoteAsset, BalanceBucket.AVAILABLE, cost.negate()));
	}

	// What a limit order holds back: a buy holds USDT (quantity x limit price), a sell holds the coin.
	private static BigDecimal reservedAmount(OrderEntity order) {
		return order.getSide() == OrderSide.BUY
				? order.getQuantity().multiply(order.getLimitPrice())
				: order.getQuantity();
	}

	private static List<Leg> moveReserve(AccountEntity account, OrderEntity order, boolean intoReserve) {
		var market = order.getMarket();
		var reservedAsset = order.getSide() == OrderSide.BUY ? market.getQuoteAsset() : market.getBaseAsset();
		return moveBetweenBuckets(account, reservedAsset, reservedAmount(order), intoReserve);
	}

	// available -> reserved, or the other way round
	private static List<Leg> moveBetweenBuckets(AccountEntity account, AssetEntity asset, BigDecimal amount,
			boolean intoReserve) {
		var signedAmount = intoReserve ? amount : amount.negate();
		return List.of(new Leg(account, asset, BalanceBucket.AVAILABLE, signedAmount.negate()),
				new Leg(account, asset, BalanceBucket.RESERVED, signedAmount));
	}

	private MarketEntity lockMarket(String pair) {
		return marketRepository.findByPairLockedForWrite(pair)
				.orElseThrow(() -> new ResourceNotFoundException("Market not found"));
	}

	// Same request gives the same hash, however the number is written (2 and 2.0 are the same).
	private static String hashOf(String pair, SubmitOrderRequest request) {
		var text = String.join("|", pair, request.side().name(), request.type().name(),
				request.quantity().stripTrailingZeros().toPlainString());
		if (request.limitPrice() != null) {
			text += "|" + request.limitPrice().stripTrailingZeros().toPlainString();
		}
		return AuthServiceImpl.hash(text);
	}

	private OrderResponse replay(OrderEntity order, String hash) {
		if (!order.getRequestHash().equals(hash)) {
			throw new ConflictException("Idempotency-Key was already used for a different request");
		}
		return orderMapper.toOrderResponse(order);
	}

	@Override
	public Page<OrderResponse> list(UUID userId, OrderStatus status, int page, int size) {
		var pageRequest = newestFirst(page, size);
		var orders = status == null
				? orderRepository.findByUserId(userId, pageRequest)
				: orderRepository.findByUserIdAndStatus(userId, status, pageRequest);
		return orders.map(orderMapper::toOrderResponse);
	}

	@Override
	public OrderResponse get(UUID userId, UUID orderId) {
		return orderRepository.findByIdAndUserId(orderId, userId).map(orderMapper::toOrderResponse)
				.orElseThrow(() -> new ResourceNotFoundException("Order not found"));
	}

	@Override
	public Page<TradeResponse> trades(UUID userId, int page, int size) {
		return tradeRepository.findByOrderUserId(userId, newestFirst(page, size)).map(orderMapper::toTradeResponse);
	}

	private static PageRequest newestFirst(int page, int size) {
		return PageRequest.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
	}
}
