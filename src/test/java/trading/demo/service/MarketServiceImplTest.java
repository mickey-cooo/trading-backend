package trading.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import trading.demo.exception.ResourceNotFoundException;
import trading.demo.exception.ValidationException;
import trading.demo.mapper.MarketMapper;
import trading.demo.model.entity.InstrumentEntity;
import trading.demo.model.entity.QuoteEntity;
import trading.demo.repository.InstrumentRepository;
import trading.demo.repository.QuoteRepository;

class MarketServiceImplTest {
	private InstrumentRepository instrumentRepository;
	private QuoteRepository quoteRepository;
	private MarketServiceImpl marketService;
	private InstrumentEntity bitcoinTetherPair;

	@BeforeEach
	void setUp() {
		instrumentRepository = mock(InstrumentRepository.class);
		quoteRepository = mock(QuoteRepository.class);
		marketService = new MarketServiceImpl(instrumentRepository, quoteRepository, new MarketMapper());

		bitcoinTetherPair = new InstrumentEntity();
		bitcoinTetherPair.setSymbol("BTC-USDT");
		when(instrumentRepository.findBySymbolForUpdate("BTC-USDT")).thenReturn(Optional.of(bitcoinTetherPair));
		when(instrumentRepository.findBySymbol("BTC-USDT")).thenReturn(Optional.of(bitcoinTetherPair));
		// save() hands back the quote it was given, like the real repository
		when(quoteRepository.save(any(QuoteEntity.class))).thenAnswer(call -> call.getArgument(0));
	}

	@Test
	void firstQuoteGetsVersionOne() {
		when(quoteRepository.findFirstByInstrumentOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.empty());

		var response = marketService.publishQuote("BTC-USDT", new BigDecimal("65000.50"));

		assertEquals(1, response.version());
		assertEquals("65000.50", response.price());
		assertEquals("BTC-USDT", response.symbol());
	}

	@Test
	void nextQuoteIsLastVersionPlusOne() {
		var lastQuote = new QuoteEntity();
		lastQuote.setVersion(41);
		when(quoteRepository.findFirstByInstrumentOrderByVersionDesc(bitcoinTetherPair))
				.thenReturn(Optional.of(lastQuote));

		var response = marketService.publishQuote("BTC-USDT", new BigDecimal("65000.50"));

		assertEquals(42, response.version());
	}

	@Test
	void storesPriceWithExactlyTwoDecimals() {
		when(quoteRepository.findFirstByInstrumentOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.empty());

		marketService.publishQuote("BTC-USDT", new BigDecimal("65000.500")); // trailing zero is not a 3rd decimal
		marketService.publishQuote("BTC-USDT", new BigDecimal("7")); // whole number

		var savedQuotes = ArgumentCaptor.forClass(QuoteEntity.class);
		verify(quoteRepository, org.mockito.Mockito.times(2)).save(savedQuotes.capture());
		assertEquals("65000.50", savedQuotes.getAllValues().get(0).getPrice().toPlainString());
		assertEquals("7.00", savedQuotes.getAllValues().get(1).getPrice().toPlainString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "0.00", "-1", "-65000.50", "65000.505", "0.001", "1E-9", "1000000000000000000", // 19
																													// digits
																													// before
																													// the
																													// dot
			"1E+30", "1E+2147483647", "1E-2147483647"})
	void rejectsPricesBreakingThePriceRules(String badPrice) {
		assertThrows(ValidationException.class, () -> marketService.publishQuote("BTC-USDT", new BigDecimal(badPrice)));

		verify(quoteRepository, never()).save(any());
	}

	@Test
	void rejectsMissingPrice() {
		assertThrows(ValidationException.class, () -> marketService.publishQuote("BTC-USDT", null));
	}

	@Test
	void symbolIsMatchedIgnoringCaseAndSpaces() {
		when(quoteRepository.findFirstByInstrumentOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.empty());

		var response = marketService.publishQuote("  btc-usdt ", new BigDecimal("1"));

		assertEquals("BTC-USDT", response.symbol());
	}

	@Test
	void unknownSymbolIsNotFound() {
		when(instrumentRepository.findBySymbolForUpdate("DOGE-USDT")).thenReturn(Optional.empty());
		when(instrumentRepository.findBySymbol("DOGE-USDT")).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class,
				() -> marketService.publishQuote("DOGE-USDT", new BigDecimal("1")));
		assertThrows(ResourceNotFoundException.class, () -> marketService.getCurrentQuote("DOGE-USDT"));
	}

	@Test
	void currentQuoteIsNotFoundBeforeAnyPriceIsPublished() {
		when(quoteRepository.findFirstByInstrumentOrderByVersionDesc(bitcoinTetherPair)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> marketService.getCurrentQuote("BTC-USDT"));
	}

	@Test
	void currentQuoteIsTheHighestVersion() {
		var latestQuote = new QuoteEntity();
		latestQuote.setInstrument(bitcoinTetherPair);
		latestQuote.setPrice(new BigDecimal("65000.50"));
		latestQuote.setVersion(7);
		when(quoteRepository.findFirstByInstrumentOrderByVersionDesc(bitcoinTetherPair))
				.thenReturn(Optional.of(latestQuote));

		var response = marketService.getCurrentQuote("BTC-USDT");

		assertEquals("65000.50", response.price());
		assertEquals(7, response.version());
	}
}
