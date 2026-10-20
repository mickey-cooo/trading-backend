package trading.demo.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import trading.demo.service.QuoteStream;

// Feeds the charts from Kafka; one group per instance so each sees every quote.
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class QuoteKafkaConsumer {
	private final QuoteStream quoteStream;

	public QuoteKafkaConsumer(QuoteStream quoteStream) {
		this.quoteStream = quoteStream;
	}

	@KafkaListener(topics = KafkaConfig.QUOTES_TOPIC, groupId = "stream-${random.uuid}")
	public void onQuote(ConsumerRecord<String, String> quote) {
		quoteStream.broadcast(quote.key());
	}
}
