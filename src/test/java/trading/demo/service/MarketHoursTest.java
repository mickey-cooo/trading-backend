package trading.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Instant;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import trading.demo.config.MarketHoursProperties;
import trading.demo.controller.MarketController;
import trading.demo.exception.ConflictException;

class MarketHoursTest {
	private final MarketHours marketHours = new MarketHours(
			new MarketHoursProperties(true, LocalTime.of(20, 0), LocalTime.of(23, 0)));

	@Test
	void isOpenFromOpenTimeUntilJustBeforeCloseTime() {
		assertFalse(marketHours.isOpenAt(Instant.parse("2026-10-21T19:59:59Z")));
		assertTrue(marketHours.isOpenAt(Instant.parse("2026-10-21T20:00:00Z")));
		assertTrue(marketHours.isOpenAt(Instant.parse("2026-10-21T22:59:59Z")));
		assertFalse(marketHours.isOpenAt(Instant.parse("2026-10-21T23:00:00Z")));
	}

	@Test
	void closedMarketRefusesOrdersWithTheTradingHours() {
		var exception = assertThrows(ConflictException.class,
				() -> marketHours.requireOpenAt(Instant.parse("2026-10-21T12:00:00Z")));
		assertEquals("Outside trading hours. Trading is open 20:00-23:00 UTC", exception.getMessage());
	}

	@Test
	void nextChangeIsTheNextOpenOrClose() {
		assertEquals(Instant.parse("2026-10-21T20:00:00Z"),
				marketHours.describeAt(Instant.parse("2026-10-21T12:00:00Z")).nextChange());
		assertEquals(Instant.parse("2026-10-22T20:00:00Z"),
				marketHours.describeAt(Instant.parse("2026-10-21T23:30:00Z")).nextChange());
		assertEquals(Instant.parse("2026-10-21T23:00:00Z"),
				marketHours.describeAt(Instant.parse("2026-10-21T21:00:00Z")).nextChange());
	}

	@Test
	void disabledWindowIsAlwaysOpen() {
		var allDay = new MarketHours(new MarketHoursProperties(false, LocalTime.of(20, 0), LocalTime.of(23, 0)));
		var response = allDay.describeAt(Instant.parse("2026-10-21T12:00:00Z"));
		assertTrue(response.isOpen());
		assertNull(response.nextChange());
	}

	@Test
	void openMustBeBeforeClose() {
		assertThrows(IllegalArgumentException.class,
				() -> new MarketHoursProperties(true, LocalTime.of(23, 0), LocalTime.of(20, 0)));
	}

	@Test
	void hoursEndpointExposesIsOpenAndTheWindow() throws Exception {
		var mvc = MockMvcBuilders
				.standaloneSetup(new MarketController(mock(MarketService.class), mock(QuoteStream.class), marketHours))
				.build();
		mvc.perform(get("/markets/hours")).andExpect(jsonPath("$.isOpen").isBoolean())
				.andExpect(jsonPath("$.open").value("20:00")).andExpect(jsonPath("$.close").value("23:00"));
	}
}
