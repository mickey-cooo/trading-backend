package trading.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ResponseStatusException;

import trading.demo.config.WebConfig;
import trading.demo.controller.MarketController;
import trading.demo.exception.ResourceNotFoundException;
import trading.demo.model.dto.marketDto.CandleResponse;

class QuoteStreamTest {
	private MarketService marketService;
	private QuoteStream quoteStream;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		marketService = mock(MarketService.class);
		quoteStream = new QuoteStream(marketService, Runnable::run);
		mvc = MockMvcBuilders.standaloneSetup(new MarketController(marketService, quoteStream, mock(MarketHours.class)))
				.setCustomHandlerMapping(QuoteStreamTest::prefixedHandlerMapping).build();
	}

	@Test
	void listenerGetsTheCurrentCandleAtOnceAndAgainAfterEachBroadcast() throws Exception {
		when(marketService.getCandles("BTC-USDT", "1m", 1))
				.thenReturn(List.of(new CandleResponse(60, "100.00", "101.00", "99.00", "100.50")));

		var result = mvc.perform(get("/api/v1/markets/btc-usdt/stream").accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(request().asyncStarted()).andReturn();
		assertTrue(result.getResponse().getContentAsString().contains("\"close\":\"100.50\""));

		when(marketService.getCandles("BTC-USDT", "1m", 1))
				.thenReturn(List.of(new CandleResponse(60, "100.00", "102.00", "99.00", "101.75")));
		quoteStream.broadcast("BTC-USDT");

		assertTrue(result.getResponse().getContentAsString().contains("\"close\":\"101.75\""));
	}

	@Test
	void broadcastReturnsAtOnceAndSendsLaterOnTheSenderThread() throws Exception {
		var queued = new ArrayList<Runnable>();
		var stream = new QuoteStream(marketService, queued::add);
		mvc = MockMvcBuilders.standaloneSetup(new MarketController(marketService, stream, mock(MarketHours.class)))
				.setCustomHandlerMapping(QuoteStreamTest::prefixedHandlerMapping).build();
		when(marketService.getCandles("BTC-USDT", "1m", 1))
				.thenReturn(List.of(new CandleResponse(60, "100.00", "101.00", "99.00", "100.50")));
		var result = mvc.perform(get("/api/v1/markets/BTC-USDT/stream").accept(MediaType.TEXT_EVENT_STREAM))
				.andReturn();
		var before = result.getResponse().getContentAsString();

		stream.broadcast("BTC-USDT");
		assertEquals(before, result.getResponse().getContentAsString());
		assertEquals(1, queued.size());

		queued.get(0).run();
		assertNotEquals(before, result.getResponse().getContentAsString());
	}

	@Test
	void otherPairsAndIntervalsAreNotNotified() throws Exception {
		when(marketService.getCandles("BTC-USDT", "1m", 1))
				.thenReturn(List.of(new CandleResponse(60, "100.00", "101.00", "99.00", "100.50")));
		var result = mvc.perform(get("/api/v1/markets/BTC-USDT/stream").accept(MediaType.TEXT_EVENT_STREAM)).andReturn();
		var before = result.getResponse().getContentAsString();

		quoteStream.broadcast("ETH-USDT");

		assertEquals(before, result.getResponse().getContentAsString());
	}

	@Test
	void closedConnectionIsRemoved() throws Exception {
		when(marketService.getCandles("BTC-USDT", "1m", 1))
				.thenReturn(List.of(new CandleResponse(60, "100.00", "101.00", "99.00", "100.50")));
		var emitter = quoteStream.subscribe("BTC-USDT", "1m");
		assertEquals(1, quoteStream.subscriberCount());

		// a send to a connection that never started fails, which is how a dead client shows up
		emitter.complete();
		quoteStream.broadcast("BTC-USDT");

		assertEquals(0, quoteStream.subscriberCount());
	}

	@Test
	void unknownPairIsRejectedAndNothingIsKept() {
		when(marketService.getCandles("NOPE", "1m", 1)).thenThrow(new ResourceNotFoundException("Market not found"));

		assertThrows(ResourceNotFoundException.class, () -> quoteStream.subscribe("NOPE", "1m"));
		assertEquals(0, quoteStream.subscriberCount());
	}

	@Test
	void refusesNewConnectionsWhenFull() {
		when(marketService.getCandles("BTC-USDT", "1m", 1)).thenReturn(List.of());
		for (int i = 0; i < QuoteStream.MAX_SUBSCRIBERS; i++) {
			quoteStream.subscribe("BTC-USDT", "1m");
		}

		assertThrows(ResponseStatusException.class, () -> quoteStream.subscribe("BTC-USDT", "1m"));
	}

	// standalone MockMvc skips WebConfig, so give it the same /api/v1 prefix
	private static RequestMappingHandlerMapping prefixedHandlerMapping() {
		var handlerMapping = new RequestMappingHandlerMapping();
		handlerMapping.setPathPrefixes(Map.of(WebConfig.API_PREFIX, type -> true));
		return handlerMapping;
	}
}
