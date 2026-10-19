package trading.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class MarketPropertiesTest {
	private static final jakarta.validation.Validator VALIDATOR = Validation.buildDefaultValidatorFactory()
			.getValidator();

	@Test
	void defaultsAreValid() {
		assertTrue(VALIDATOR.validate(new MarketProperties(2, 8, 18)).isEmpty());
	}

	@Test
	void rejectsValuesTheDatabaseColumnsCannotHold() {
		// 3 price decimals would be rounded by quotes.price (scale 2)
		assertEquals(1, VALIDATOR.validate(new MarketProperties(3, 8, 18)).size());
		assertEquals(1, VALIDATOR.validate(new MarketProperties(2, 19, 18)).size());
		assertEquals(1, VALIDATOR.validate(new MarketProperties(2, 8, 0)).size());
	}
}
