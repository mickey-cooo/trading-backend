package trading.demo.model.enums;

import java.util.Arrays;

import trading.demo.exception.ValidationException;

// The only candle sizes a caller can ask for; seconds is the only value that reaches SQL.
public enum CandleInterval {
	ONE_MINUTE("1m", 60), FIVE_MINUTES("5m", 300), ONE_HOUR("1h", 3600);

	private final String code;
	private final long seconds;

	CandleInterval(String code, long seconds) {
		this.code = code;
		this.seconds = seconds;
	}

	public long getSeconds() {
		return seconds;
	}

	public static CandleInterval fromCode(String code) {
		return Arrays.stream(values()).filter(interval -> interval.code.equals(code)).findFirst()
				.orElseThrow(() -> new ValidationException("Interval must be one of 1m, 5m, 1h"));
	}
}
