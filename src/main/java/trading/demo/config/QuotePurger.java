package trading.demo.config;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import trading.demo.repository.QuoteRepository;

// The price feed adds a quote a second per market and nothing deleted them, so drop quotes older than a week.
@Component
public class QuotePurger {
	private static final Logger log = LoggerFactory.getLogger(QuotePurger.class);
	static final Duration KEEP = Duration.ofDays(7);

	private final QuoteRepository quoteRepository;

	public QuotePurger(QuoteRepository quoteRepository) {
		this.quoteRepository = quoteRepository;
	}

	@Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
	@Transactional
	public void purge() {
		// cut on the hour, a boundary of every candle size, so the oldest kept candle is whole
		var deleted = quoteRepository.purgeOlderThan(Instant.now().minus(KEEP).truncatedTo(ChronoUnit.HOURS));
		log.info("Purged {} quotes older than {} days", deleted, KEEP.toDays());
	}
}
