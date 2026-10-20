package trading.demo.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import trading.demo.service.QuotePublished;
import trading.demo.service.QuoteStream;

class QuoteKafkaTest {
	@SuppressWarnings("unchecked")
	@Test
	void publisherKeysTheMessageByPair() {
		var kafka = mock(KafkaTemplate.class);
		when(kafka.send(any(String.class), any(String.class), any(String.class)))
				.thenReturn(new CompletableFuture<SendResult<String, String>>());

		new QuoteKafkaPublisher(kafka).onQuotePublished(new QuotePublished("BTC-USDT", "65000.50"));

		verify(kafka).send("market.quotes", "BTC-USDT", "65000.50");
	}

	@Test
	void consumerBroadcastsThePairFromTheKey() {
		var quoteStream = mock(QuoteStream.class);

		new QuoteKafkaConsumer(quoteStream)
				.onQuote(new ConsumerRecord<>("market.quotes", 0, 0, "BTC-USDT", "65000.50"));

		verify(quoteStream).broadcast("BTC-USDT");
	}

	@Test
	void directListenerBroadcastsWhenKafkaIsOff() {
		var quoteStream = mock(QuoteStream.class);

		new QuoteStreamListener(quoteStream).onQuotePublished(new QuotePublished("ETH-USDT", "3000.00"));

		verify(quoteStream).broadcast("ETH-USDT");
	}
}
