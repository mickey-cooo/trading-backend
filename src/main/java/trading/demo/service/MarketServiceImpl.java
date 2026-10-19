package trading.demo.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import trading.demo.exception.ResourceNotFoundException;
import trading.demo.exception.ValidationException;
import trading.demo.mapper.MarketMapper;
import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.model.entity.MarketEntity;
import trading.demo.model.entity.QuoteEntity;
import trading.demo.repository.MarketRepository;
import trading.demo.repository.QuoteRepository;

@Service
public class MarketServiceImpl implements MarketService {
	// a 3rd decimal is rejected, never rounded
	private static final int PRICE_DECIMALS = 2;
	// at most 18 digits before the dot, far above any real price
	private static final int MAX_PRICE_DIGITS_BEFORE_DOT = 18;
	// the first quote of a market gets version 1
	private static final long VERSION_BEFORE_FIRST_QUOTE = 0;

	private final MarketRepository marketRepository;
	private final QuoteRepository quoteRepository;
	private final MarketMapper marketMapper;

	public MarketServiceImpl(MarketRepository marketRepository, QuoteRepository quoteRepository,
			MarketMapper marketMapper) {
		this.marketRepository = marketRepository;
		this.quoteRepository = quoteRepository;
		this.marketMapper = marketMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<MarketResponse> listMarkets() {
		return marketRepository.findAllByOrderBySymbolAsc().stream().map(marketMapper::toMarketResponse).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public QuoteResponse getCurrentQuote(String symbol) {
		var market = marketRepository.findBySymbol(normalizeSymbol(symbol))
				.orElseThrow(MarketServiceImpl::marketNotFound);
		var currentQuote = quoteRepository.findFirstByMarketOrderByVersionDesc(market)
				.orElseThrow(() -> new ResourceNotFoundException("No quote has been published for this market yet"));
		return marketMapper.toQuoteResponse(currentQuote);
	}

	@Override
	@Transactional
	public QuoteResponse publishQuote(String symbol, BigDecimal price) {
		requireValidPrice(price);
		// lock the row so two publishes cannot get the same version
		MarketEntity market = marketRepository.findBySymbolLockedForWrite(normalizeSymbol(symbol))
				.orElseThrow(MarketServiceImpl::marketNotFound);

		var lastVersion = quoteRepository.findFirstByMarketOrderByVersionDesc(market).map(QuoteEntity::getVersion)
				.orElse(VERSION_BEFORE_FIRST_QUOTE);

		var newQuote = new QuoteEntity();
		newQuote.setMarket(market);
		newQuote.setPrice(price.setScale(PRICE_DECIMALS));
		newQuote.setVersion(lastVersion + 1);
		return marketMapper.toQuoteResponse(quoteRepository.save(newQuote));
	}

	private static void requireValidPrice(BigDecimal price) {
		if (price == null || price.signum() <= 0) {
			throw new ValidationException("Price must be above zero");
		}
		// "65000.500" is fine (same number as 65000.50), "65000.505" is not
		var withoutTrailingZeros = price.stripTrailingZeros();
		var decimals = Math.max(withoutTrailingZeros.scale(), 0);
		// long, not int: 1E+2147483647 would overflow an int
		long digitsBeforeDot = (long) withoutTrailingZeros.precision() - withoutTrailingZeros.scale();
		if (decimals > PRICE_DECIMALS || digitsBeforeDot > MAX_PRICE_DIGITS_BEFORE_DOT) {
			throw new ValidationException("Price has too many digits");
		}
	}

	// "btc-usdt" and " BTC-USDT " both mean BTC-USDT
	private static String normalizeSymbol(String symbol) {
		return symbol.strip().toUpperCase(Locale.ROOT);
	}

	private static ResourceNotFoundException marketNotFound() {
		return new ResourceNotFoundException("Market not found");
	}
}
