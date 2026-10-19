package trading.demo.model.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import trading.demo.exception.ValidationException;

class CandleIntervalTest {
	@Test
	void knownCodesMapToSeconds() {
		assertEquals(60, CandleInterval.fromCode("1m").getSeconds());
		assertEquals(300, CandleInterval.fromCode("5m").getSeconds());
		assertEquals(3600, CandleInterval.fromCode("1h").getSeconds());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "2m", "1M", "60", "1m; DROP TABLE quotes"})
	void unknownCodeIsRejected(String code) {
		assertThrows(ValidationException.class, () -> CandleInterval.fromCode(code));
	}
}
