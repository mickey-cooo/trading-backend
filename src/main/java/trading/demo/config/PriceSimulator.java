package trading.demo.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import trading.demo.exception.ResourceNotFoundException;
import trading.demo.model.enums.Common;
import trading.demo.service.MarketHours;
import trading.demo.service.MarketService;

// Every second, moves the price of each active market a little. Goes through publishQuote, so all price rules apply.
@Component
@ConditionalOnProperty(prefix = "app.simulator", name = "enabled", havingValue = "true")
public class PriceSimulator {
	private static final Logger log = LoggerFactory.getLogger(PriceSimulator.class);

	private final MarketService marketService;
	private final MarketHours marketHours;
	private final SimulatorProperties properties;
	private final MarketProperties marketProperties;
	private final Random random;
	private final Map<String, BigDecimal> startPrices = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	// stays false until startup runners (like QuoteHistorySeeder) are done, so both never write quote versions at once
	private volatile boolean isStartupDone = false;

	public PriceSimulator(MarketService marketService, MarketHours marketHours, SimulatorProperties properties,
			MarketProperties marketProperties) {
		this.marketService = marketService;
		this.marketHours = marketHours;
		this.properties = properties;
		this.marketProperties = marketProperties;
		this.random = new Random(properties.seed());
		this.startPrices.putAll(properties.startPrices());
	}

	@EventListener(ApplicationReadyEvent.class)
	public void startTicking() {
		isStartupDone = true;
	}

	@Scheduled(fixedDelay = 1000)
	public void tick() {
		// market closed: no new quotes, so the last price stays frozen until it opens again
		if (!isStartupDone || !marketHours.isOpenAt(Instant.now())) {
			return;
		}
		for (var market : marketService.listMarkets()) {
			if (market.status() != Common.ACTIVE) {
				continue;
			}
			try {
				publishNextPrice(market.pair());
			} catch (RuntimeException e) {
				// one bad market must not stop the others
				log.warn("Price simulator failed for {}", market.pair(), e);
			}
		}
	}

	private void publishNextPrice(String pair) {
		BigDecimal current;
		try {
			current = new BigDecimal(marketService.getCurrentQuote(pair).price());
		} catch (ResourceNotFoundException noQuoteYet) {
			current = startPrices.get(pair);
			if (current == null) {
				return;
			}
		}
		marketService.publishQuote(pair, nextPrice(current));
	}

	// random walk: price x (1 + volatility x bell-curve step), never below one price step
	private BigDecimal nextPrice(BigDecimal current) {
		var factor = BigDecimal.valueOf(1 + properties.volatility() * random.nextGaussian());
		var next = current.multiply(factor).setScale(marketProperties.priceDecimals(), RoundingMode.HALF_UP);
		var oneStep = BigDecimal.ONE.movePointLeft(marketProperties.priceDecimals());
		return next.max(oneStep);
	}
}
