package trading.demo.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import trading.demo.model.dto.marketDto.CandleResponse;

// Open chart connections per pair and interval, sent from one separate thread.
@Component
public class QuoteStream {
	private static final Logger log = LoggerFactory.getLogger(QuoteStream.class);

	// EventSource reconnects by itself, so a connection can safely expire
	private static final long TIMEOUT_MILLIS = 5 * 60 * 1000;
	// public endpoint: bound the memory a crowd of clients can hold
	static final int MAX_SUBSCRIBERS = 500;

	private record Topic(String pair, String interval) {
	}

	private final MarketService marketService;
	// shortcut: a stalled client holds this thread until its write times out
	private final Executor sender;
	private final Map<Topic, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

	@Autowired
	public QuoteStream(MarketService marketService) {
		this(marketService, Executors
				.newSingleThreadExecutor(task -> Thread.ofPlatform().name("quote-stream").daemon().unstarted(task)));
	}

	// tests pass a direct or queueing executor
	QuoteStream(MarketService marketService, Executor sender) {
		this.marketService = marketService;
		this.sender = sender;
	}

	@PreDestroy
	void stop() {
		if (sender instanceof ExecutorService service) {
			service.shutdown();
		}
	}

	// Sends the current candle, then each broadcast; bad input fails first.
	public SseEmitter subscribe(String pair, String interval) {
		var topic = new Topic(pair.strip().toUpperCase(Locale.ROOT), interval);
		// check first: a full server must not run the query for every caller
		if (subscriberCount() >= MAX_SUBSCRIBERS) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Too many open streams");
		}
		var candle = latestCandle(topic);
		var emitter = new SseEmitter(TIMEOUT_MILLIS);
		emitters.computeIfAbsent(topic, key -> ConcurrentHashMap.newKeySet()).add(emitter);
		emitter.onCompletion(() -> remove(topic, emitter));
		emitter.onTimeout(() -> remove(topic, emitter));
		emitter.onError(error -> remove(topic, emitter));
		candle.ifPresent(first -> send(topic, emitter, first));
		return emitter;
	}

	// Call after a quote is committed; returns at once, sending is async.
	public void broadcast(String pair) {
		try {
			sender.execute(() -> sendCurrentCandles(pair));
		} catch (RejectedExecutionException shuttingDown) {
			// app is stopping; nobody is left to notify
		}
	}

	private void sendCurrentCandles(String pair) {
		emitters.forEach((topic, listeners) -> {
			if (!topic.pair().equals(pair) || listeners.isEmpty()) {
				return;
			}
			try {
				latestCandle(topic).ifPresent(candle -> listeners.forEach(emitter -> send(topic, emitter, candle)));
			} catch (RuntimeException e) {
				// one failed read must not stop the other topics
				log.warn("Live candle failed for {} {}", topic.pair(), topic.interval(), e);
			}
		});
	}

	int subscriberCount() {
		return emitters.values().stream().mapToInt(Set::size).sum();
	}

	private Optional<CandleResponse> latestCandle(Topic topic) {
		return marketService.getCandles(topic.pair(), topic.interval(), 1).stream().findFirst();
	}

	private void send(Topic topic, SseEmitter emitter, CandleResponse candle) {
		try {
			emitter.send(SseEmitter.event().data(candle, MediaType.APPLICATION_JSON));
		} catch (IOException | RuntimeException e) {
			// client went away
			remove(topic, emitter);
		}
	}

	private void remove(Topic topic, SseEmitter emitter) {
		var listeners = emitters.get(topic);
		if (listeners != null) {
			listeners.remove(emitter);
		}
	}
}
