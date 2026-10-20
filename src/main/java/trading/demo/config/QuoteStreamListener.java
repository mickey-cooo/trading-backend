package trading.demo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import trading.demo.service.QuotePublished;
import trading.demo.service.QuoteStream;

// Default path: a committed quote goes straight to the open charts. With Kafka on, QuoteKafkaConsumer does this instead.
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
public class QuoteStreamListener {
	private final QuoteStream quoteStream;

	public QuoteStreamListener(QuoteStream quoteStream) {
		this.quoteStream = quoteStream;
	}

	@TransactionalEventListener
	public void onQuotePublished(QuotePublished event) {
		quoteStream.broadcast(event.pair());
	}
}
