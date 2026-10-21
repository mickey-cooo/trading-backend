package trading.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Random;

import org.junit.jupiter.api.Test;

class QuoteHistorySeederTest {
	@Test
	void walkGivesRequestedCountOfPositivePricesAtTheMarketScale() {
		var prices = QuoteHistorySeeder.walk(new BigDecimal("65000"), 960, 0.0005, new Random(42), 2);

		assertEquals(960, prices.size());
		assertTrue(prices.stream().allMatch(p -> p.signum() > 0 && p.scale() == 2));
	}

	@Test
	void walkNeverDropsBelowOnePriceStep() {
		var prices = QuoteHistorySeeder.walk(new BigDecimal("0.01"), 200, 0.1, new Random(1), 2);

		assertTrue(prices.stream().allMatch(p -> p.compareTo(new BigDecimal("0.01")) >= 0));
	}
}
