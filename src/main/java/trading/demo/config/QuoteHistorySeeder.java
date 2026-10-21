package trading.demo.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import trading.demo.repository.MarketRepository;
import trading.demo.repository.QuoteRepository;

// On startup, gives a market that has no quotes a few hours of past prices so its chart is not empty.
// Off unless app.seed.quote-history.enabled=true; safe to rerun because a market with any quote is skipped.
@Component
@Order(2) // after MarketSeeder has created the pairs
@ConditionalOnProperty(prefix = "app.seed.quote-history", name = "enabled", havingValue = "true")
public class QuoteHistorySeeder implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(QuoteHistorySeeder.class);
	// 4 quotes a minute for 4 hours, so a 1m candle has an open, a close and a spread between
	static final int QUOTE_COUNT = 960;
	static final Duration QUOTE_GAP = Duration.ofSeconds(15);

	private final MarketRepository marketRepository;
	private final QuoteRepository quoteRepository;
	private final SimulatorProperties simulator;
	private final MarketProperties marketProperties;

	public QuoteHistorySeeder(MarketRepository marketRepository, QuoteRepository quoteRepository,
			SimulatorProperties simulator, MarketProperties marketProperties) {
		this.marketRepository = marketRepository;
		this.quoteRepository = quoteRepository;
		this.simulator = simulator;
		this.marketProperties = marketProperties;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		var random = new Random(simulator.seed());
		// the newest seeded quote lands at startup time, so the live feed carries on from it
		var first = Instant.now().minus(QUOTE_GAP.multipliedBy(QUOTE_COUNT - 1));
		for (var market : marketRepository.findAll()) {
			var start = simulator.startPrices().get(market.getPair());
			if (start == null || quoteRepository.findFirstByMarketOrderByVersionDesc(market).isPresent()) {
				continue;
			}
			var prices = walk(start, QUOTE_COUNT, simulator.volatility(), random, marketProperties.priceDecimals());
			for (int i = 0; i < prices.size(); i++) {
				quoteRepository.insertAt(UUID.randomUUID(), market.getId(), prices.get(i), i + 1L,
						first.plus(QUOTE_GAP.multipliedBy(i)));
			}
			log.info("Seeded {} past quotes for {}", prices.size(), market.getPair());
		}
	}

	// same random walk as PriceSimulator: price x (1 + volatility x bell-curve step), never below one price step
	static List<BigDecimal> walk(BigDecimal start, int count, double volatility, Random random, int decimals) {
		var oneStep = BigDecimal.ONE.movePointLeft(decimals);
		var prices = new ArrayList<BigDecimal>(count);
		var price = start;
		for (int i = 0; i < count; i++) {
			var factor = BigDecimal.valueOf(1 + volatility * random.nextGaussian());
			price = price.multiply(factor).setScale(decimals, RoundingMode.HALF_UP).max(oneStep);
			prices.add(price);
		}
		return prices;
	}
}
