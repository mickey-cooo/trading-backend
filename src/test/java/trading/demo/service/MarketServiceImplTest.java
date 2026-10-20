package trading.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import trading.demo.config.MarketProperties;
import trading.demo.exception.ResourceNotFoundException;
import trading.demo.exception.ValidationException;
import trading.demo.mapper.MarketMapper;
import trading.demo.model.entity.MarketEntity;
import trading.demo.model.entity.QuoteEntity;
import trading.demo.repository.MarketRepository;
import trading.demo.repository.QuoteRepository;
import trading.demo.repository.QuoteRepository.CandleRow;

class MarketServiceImplTest {
	private MarketRepository marketRepository;
	private QuoteRepository quoteRepository;
	private ApplicationEventPublisher eventPublisher;
	private MarketServiceImpl marketService;
	private MarketEntity bitcoinTetherPair;

	@BeforeEach
	void setUp() {
		marketRepository = mock(MarketRepository.class);
		quoteRepository = mock(QuoteRepository.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		marketService = new MarketServiceImpl(marketRepository, quoteRepository, new MarketMapper(),
				new MarketProperties(2, 8, 18), eventPublisher);

		bitcoinTetherPair = new MarketEntity();
		bitcoinTetherPair.setPair("BTC-USDT");
		when(marketRepository.findByPairLockedForWrite("BTC-USDT")).thenReturn(Optional.of(bitcoinTetherPair));
		when(marketRepository.findByPair("BTC-USDT")).thenReturn(Optional.of(bitcoinTetherPair));
		// save() hands back the quote it was given, like the real repository
		when(quoteRepository.save(any(QuoteEntity.class))).thenAnswer(call -> call.getArgument(0));
	}

	@Test
	void firstQuoteGetsVersionOne() {
		when(quoteRepository.findFirstByMarketOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.empty());

		var response = marketService.publishQuote("BTC-USDT", new BigDecimal("65000.50"));

		assertEquals(1, response.version());
		assertEquals("65000.50", response.price());
		assertEquals("BTC-USDT", response.pair());
	}

	@Test
	void publishedQuoteIsAnnouncedForTheLiveChart() {
		when(quoteRepository.findFirstByMarketOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.empty());

		marketService.publishQuote("BTC-USDT", new BigDecimal("65000.50"));

		verify(eventPublisher).publishEvent(new QuotePublished("BTC-USDT", "65000.50"));
	}

	@Test
	void nextQuoteIsLastVersionPlusOne() {
		var lastQuote = new QuoteEntity();
		lastQuote.setVersion(41);
		when(quoteRepository.findFirstByMarketOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.of(lastQuote));

		var response = marketService.publishQuote("BTC-USDT", new BigDecimal("65000.50"));

		assertEquals(42, response.version());
	}

	@Test
	void storesPriceWithExactlyTwoDecimals() {
		when(quoteRepository.findFirstByMarketOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.empty());

		marketService.publishQuote("BTC-USDT", new BigDecimal("65000.500")); // trailing zero is not a 3rd decimal
		marketService.publishQuote("BTC-USDT", new BigDecimal("7")); // whole number

		var savedQuotes = ArgumentCaptor.forClass(QuoteEntity.class);
		verify(quoteRepository, org.mockito.Mockito.times(2)).save(savedQuotes.capture());
		assertEquals("65000.50", savedQuotes.getAllValues().get(0).getPrice().toPlainString());
		assertEquals("7.00", savedQuotes.getAllValues().get(1).getPrice().toPlainString());
	}

	@ParameterizedTest
	// 1000000000000000000 has 19 digits before the dot
	@ValueSource(strings = {"0", "0.00", "-1", "-65000.50", "65000.505", "0.001", "1E-9", "1000000000000000000",
			"1E+30", "1E+2147483647", "1E-2147483647"})
	void rejectsPricesBreakingThePriceRules(String badPrice) {
		assertThrows(ValidationException.class, () -> marketService.publishQuote("BTC-USDT", new BigDecimal(badPrice)));

		verify(quoteRepository, never()).save(any());
	}

	@Test
	void rejectsMissingPrice() {
		assertThrows(ValidationException.class, () -> marketService.publishQuote("BTC-USDT", null));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0.00000001", "0.123456780", "1", "1E-8"})
	void acceptsQuantitiesWithUpToEightDecimals(String quantity) {
		marketService.requireValidQuantity(new BigDecimal(quantity));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "0.000000001", "1E-9", "1000000000000000000", "1E+30", "1E+2147483647",
			"1E-2147483647"})
	void rejectsQuantitiesBreakingTheQuantityRules(String badQuantity) {
		assertThrows(ValidationException.class, () -> marketService.requireValidQuantity(new BigDecimal(badQuantity)));
	}

	@Test
	void rejectsMissingQuantity() {
		assertThrows(ValidationException.class, () -> marketService.requireValidQuantity(null));
	}

	@Test
	void pairIsMatchedIgnoringCaseAndSpaces() {
		when(quoteRepository.findFirstByMarketOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.empty());

		var response = marketService.publishQuote("  btc-usdt ", new BigDecimal("1"));

		assertEquals("BTC-USDT", response.pair());
	}

	@Test
	void unknownPairIsNotFound() {
		when(marketRepository.findByPairLockedForWrite("DOGE-USDT")).thenReturn(Optional.empty());
		when(marketRepository.findByPair("DOGE-USDT")).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class,
				() -> marketService.publishQuote("DOGE-USDT", new BigDecimal("1")));
		assertThrows(ResourceNotFoundException.class, () -> marketService.getCurrentQuote("DOGE-USDT"));
	}

	@Test
	void currentQuoteIsNotFoundBeforeAnyPriceIsPublished() {
		when(quoteRepository.findFirstByMarketOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> marketService.getCurrentQuote("BTC-USDT"));
	}

	@Test
	void currentQuoteIsTheHighestVersion() {
		var latestQuote = new QuoteEntity();
		latestQuote.setMarket(bitcoinTetherPair);
		latestQuote.setPrice(new BigDecimal("65000.50"));
		latestQuote.setVersion(7);
		when(quoteRepository.findFirstByMarketOrderByVersionDesc(bitcoinTetherPair))
				.thenReturn(Optional.of(latestQuote));

		var response = marketService.getCurrentQuote("BTC-USDT");

		assertEquals("65000.50", response.price());
		assertEquals(7, response.version());
	}

	private static CandleRow candleRow(long time, String open, String high, String low, String close) {
		var row = mock(CandleRow.class);
		when(row.getTime()).thenReturn(time);
		when(row.getOpen()).thenReturn(new BigDecimal(open));
		when(row.getHigh()).thenReturn(new BigDecimal(high));
		when(row.getLow()).thenReturn(new BigDecimal(low));
		when(row.getClose()).thenReturn(new BigDecimal(close));
		return row;
	}

	@Test
	void candlesComeBackOldestFirst() {
		// the query returns newest first; rows are built before stubbing so mocks do not nest
		var newest = candleRow(1200, "101.00", "105.00", "99.50", "104.00");
		var oldest = candleRow(1140, "100.00", "102.00", "98.00", "101.00");
		when(quoteRepository.findCandles(any(), eq(300L), eq(2))).thenReturn(List.of(newest, oldest));

		var candles = marketService.getCandles("btc-usdt", "5m", 2);

		assertEquals(2, candles.size());
		assertEquals(1140, candles.get(0).time());
		assertEquals(1200, candles.get(1).time());
		assertEquals("101.00", candles.get(1).open());
		assertEquals("105.00", candles.get(1).high());
		assertEquals("99.50", candles.get(1).low());
		assertEquals("104.00", candles.get(1).close());
	}

	@Test
	void candlesRejectUnknownIntervalBeforeTouchingTheDatabase() {
		assertThrows(ValidationException.class, () -> marketService.getCandles("BTC-USDT", "2m", 10));

		verify(quoteRepository, never()).findCandles(any(), anyLong(), anyInt());
	}

	@Test
	void candlesForUnknownMarketAreNotFound() {
		assertThrows(ResourceNotFoundException.class, () -> marketService.getCandles("DOGE-USDT", "1m", 10));
	}
}
