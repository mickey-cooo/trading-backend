package trading.demo.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import trading.demo.config.MarketProperties;
import trading.demo.exception.ResourceNotFoundException;
import trading.demo.exception.ValidationException;
import trading.demo.mapper.MarketMapper;
import trading.demo.model.dto.marketDto.CandleResponse;
import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.model.entity.MarketEntity;
import trading.demo.model.entity.QuoteEntity;
import trading.demo.model.enums.CandleInterval;
import trading.demo.repository.MarketRepository;
import trading.demo.repository.QuoteRepository;

@Service
public class MarketServiceImpl implements MarketService {
	// the first quote of a market gets version 1
	private static final long VERSION_BEFORE_FIRST_QUOTE = 0;

	private final MarketRepository marketRepository;
	private final QuoteRepository quoteRepository;
	private final MarketMapper marketMapper;
	private final MarketProperties marketProperties;
	private final ApplicationEventPublisher eventPublisher;

	public MarketServiceImpl(MarketRepository marketRepository, QuoteRepository quoteRepository,
			MarketMapper marketMapper, MarketProperties marketProperties, ApplicationEventPublisher eventPublisher) {
		this.marketRepository = marketRepository;
		this.quoteRepository = quoteRepository;
		this.marketMapper = marketMapper;
		this.marketProperties = marketProperties;
		this.eventPublisher = eventPublisher;
	}

	@Override
	@Transactional(readOnly = true)
	public List<MarketResponse> listMarkets() {
		return marketRepository.findAllByOrderByPairAsc().stream().map(marketMapper::toMarketResponse).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public QuoteResponse getCurrentQuote(String pair) {
		var market = marketRepository.findByPair(normalizePair(pair)).orElseThrow(MarketServiceImpl::marketNotFound);
		var currentQuote = quoteRepository.findFirstByMarketOrderByVersionDesc(market)
				.orElseThrow(() -> new ResourceNotFoundException("No quote has been published for this market yet"));
		return marketMapper.toQuoteResponse(currentQuote);
	}

	@Override
	@Transactional
	public QuoteResponse publishQuote(String pair, BigDecimal price) {
		requireValidAmount(price, marketProperties.priceDecimals(), "Price");
		// lock the row so two publishes cannot get the same version
		MarketEntity market = marketRepository.findByPairLockedForWrite(normalizePair(pair))
				.orElseThrow(MarketServiceImpl::marketNotFound);

		var lastVersion = quoteRepository.findFirstByMarketOrderByVersionDesc(market).map(QuoteEntity::getVersion)
				.orElse(VERSION_BEFORE_FIRST_QUOTE);

		var newQuote = new QuoteEntity();
		newQuote.setMarket(market);
		newQuote.setPrice(price.setScale(marketProperties.priceDecimals()));
		newQuote.setVersion(lastVersion + 1);
		var saved = marketMapper.toQuoteResponse(quoteRepository.save(newQuote));
		// listeners run after this transaction commits
		eventPublisher.publishEvent(new QuotePublished(saved.pair(), saved.price()));
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public List<CandleResponse> getCandles(String pair, String interval, int limit) {
		var candleInterval = CandleInterval.fromCode(interval);
		var market = marketRepository.findByPair(normalizePair(pair)).orElseThrow(MarketServiceImpl::marketNotFound);
		var candles = quoteRepository.findCandles(market.getId(), candleInterval.getSeconds(), limit).stream()
				.map(marketMapper::toCandleResponse).collect(Collectors.toList());
		// query returns newest first; charts want oldest first
		Collections.reverse(candles);
		return candles;
	}

	@Override
	public void requireValidPrice(BigDecimal price) {
		requireValidAmount(price, marketProperties.priceDecimals(), "Price");
	}

	@Override
	public void requireValidQuantity(BigDecimal quantity) {
		requireValidAmount(quantity, marketProperties.quantityDecimals(), "Quantity");
	}

	// shared by price and quantity: above zero, no extra decimals, no giant numbers
	private void requireValidAmount(BigDecimal amount, int maxDecimals, String name) {
		if (amount == null || amount.signum() <= 0) {
			throw new ValidationException(name + " must be above zero");
		}
		// "65000.500" is fine (same number as 65000.50), "65000.505" is not
		var withoutTrailingZeros = amount.stripTrailingZeros();
		var decimals = Math.max(withoutTrailingZeros.scale(), 0);
		// long, not int: 1E+2147483647 would overflow an int
		long digitsBeforeDot = (long) withoutTrailingZeros.precision() - withoutTrailingZeros.scale();
		if (decimals > maxDecimals || digitsBeforeDot > marketProperties.maxDigitsBeforeDot()) {
			throw new ValidationException(name + " has too many digits");
		}
	}

	// "btc-usdt" and " BTC-USDT " both mean BTC-USDT
	private static String normalizePair(String pair) {
		return pair.strip().toUpperCase(Locale.ROOT);
	}

	private static ResourceNotFoundException marketNotFound() {
		return new ResourceNotFoundException("Market not found");
	}
}
