package trading.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import trading.demo.service.QuotePublished;

// Sends committed quotes to Kafka, keyed by pair; the database stays the source of truth.
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class QuoteKafkaPublisher {
	private static final Logger log = LoggerFactory.getLogger(QuoteKafkaPublisher.class);

	private final KafkaTemplate<String, String> kafka;

	public QuoteKafkaPublisher(KafkaTemplate<String, String> kafka) {
		this.kafka = kafka;
	}

	@TransactionalEventListener
	public void onQuotePublished(QuotePublished event) {
		try {
			kafka.send(KafkaConfig.QUOTES_TOPIC, event.pair(), event.price()).whenComplete((result, error) -> {
				if (error != null) {
					log.warn("Could not send quote of {} to Kafka", event.pair(), error);
				}
			});
		} catch (RuntimeException e) {
			// the quote is committed, so a Kafka failure must not fail the publish
			log.warn("Could not send quote of {} to Kafka", event.pair(), e);
		}
	}
}
