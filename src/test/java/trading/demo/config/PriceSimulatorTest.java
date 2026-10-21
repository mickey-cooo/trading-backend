package trading.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import trading.demo.exception.ResourceNotFoundException;
import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.model.enums.Common;
import trading.demo.service.MarketService;

class PriceSimulatorTest {
	private MarketService marketService;

	@BeforeEach
	void setUp() {
		marketService = mock(MarketService.class);
	}

	private PriceSimulator simulator(double volatility, Map<String, BigDecimal> startPrices) {
		var simulator = new PriceSimulator(marketService,
				new SimulatorProperties(true, 42, volatility, startPrices), new MarketProperties(2, 8, 18));
		simulator.startTicking();
		return simulator;
	}

	@Test
	void doesNothingBeforeStartupIsDone() {
		new PriceSimulator(marketService, new SimulatorProperties(true, 42, 0.0005, Map.of()),
				new MarketProperties(2, 8, 18)).tick();

		verifyNoInteractions(marketService);
	}

	private static MarketResponse market(String pair, Common status) {
		return new MarketResponse(pair, "BTC", "USDT", status, "0.00000001", "0.01");
	}

	@Test
	void publishesAPriceNearTheCurrentOne() {
		when(marketService.listMarkets()).thenReturn(List.of(market("BTC-USDT", Common.ACTIVE)));
		when(marketService.getCurrentQuote("BTC-USDT"))
				.thenReturn(new QuoteResponse("BTC-USDT", "65000.00", 5, null));

		simulator(0.0005, Map.of()).tick();

		var price = ArgumentCaptor.forClass(BigDecimal.class);
		verify(marketService).publishQuote(eq("BTC-USDT"), price.capture());
		assertEquals(2, price.getValue().scale());
		// 0.05% per tick: even a 10-sigma step stays inside 0.5%
		assertTrue(price.getValue().subtract(new BigDecimal("65000")).abs().compareTo(new BigDecimal("325")) < 0);
	}

	@Test
	void sameSeedGivesSamePrices() {
		when(marketService.listMarkets()).thenReturn(List.of(market("BTC-USDT", Common.ACTIVE)));
		when(marketService.getCurrentQuote("BTC-USDT"))
				.thenReturn(new QuoteResponse("BTC-USDT", "65000.00", 5, null));

		simulator(0.01, Map.of()).tick();
		simulator(0.01, Map.of()).tick();

		var price = ArgumentCaptor.forClass(BigDecimal.class);
		verify(marketService, times(2)).publishQuote(anyString(), price.capture());
		assertEquals(price.getAllValues().get(0), price.getAllValues().get(1));
	}

	@Test
	void startsFromTheConfiguredPriceWhenThereIsNoQuote() {
		when(marketService.listMarkets()).thenReturn(List.of(market("BTC-USDT", Common.ACTIVE)));
		when(marketService.getCurrentQuote("BTC-USDT")).thenThrow(new ResourceNotFoundException("none"));

		simulator(0, Map.of("btc-usdt", new BigDecimal("65000"))).tick();

		verify(marketService).publishQuote("BTC-USDT", new BigDecimal("65000.00"));
	}

	@Test
	void skipsMarketWithNoQuoteAndNoStartPrice() {
		when(marketService.listMarkets()).thenReturn(List.of(market("BTC-USDT", Common.ACTIVE)));
		when(marketService.getCurrentQuote("BTC-USDT")).thenThrow(new ResourceNotFoundException("none"));

		simulator(0.0005, Map.of()).tick();

		verify(marketService, never()).publishQuote(anyString(), any());
	}

	@Test
	void skipsSuspendedMarkets() {
		when(marketService.listMarkets()).thenReturn(List.of(market("BTC-USDT", Common.SUSPENDED)));

		simulator(0.0005, Map.of()).tick();

		verify(marketService, never()).publishQuote(anyString(), any());
	}

	@Test
	void oneFailingMarketDoesNotStopTheOthers() {
		when(marketService.listMarkets())
				.thenReturn(List.of(market("BTC-USDT", Common.ACTIVE), market("ETH-USDT", Common.ACTIVE)));
		when(marketService.getCurrentQuote("BTC-USDT")).thenThrow(new IllegalStateException("db down"));
		when(marketService.getCurrentQuote("ETH-USDT")).thenReturn(new QuoteResponse("ETH-USDT", "3000.00", 1, null));

		simulator(0.0005, Map.of()).tick();

		verify(marketService).publishQuote(eq("ETH-USDT"), any());
	}

	@Test
	void priceNeverDropsBelowOneStep() {
		when(marketService.listMarkets()).thenReturn(List.of(market("BTC-USDT", Common.ACTIVE)));
		when(marketService.getCurrentQuote("BTC-USDT")).thenReturn(new QuoteResponse("BTC-USDT", "0.01", 1, null));

		simulator(0.1, Map.of()).tick();

		var price = ArgumentCaptor.forClass(BigDecimal.class);
		verify(marketService).publishQuote(anyString(), price.capture());
		assertTrue(price.getValue().compareTo(new BigDecimal("0.01")) >= 0);
	}
}
