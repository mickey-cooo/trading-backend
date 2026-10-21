package trading.demo.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import trading.demo.config.MarketHoursProperties;
import trading.demo.exception.ConflictException;
import trading.demo.model.dto.marketDto.MarketHoursResponse;

// Answers "is trading open?" from the daily UTC window. Open time counts as open, close time as closed.
@Component
public class MarketHours {
	private final MarketHoursProperties properties;

	public MarketHours(MarketHoursProperties properties) {
		this.properties = properties;
	}

	public boolean isOpenAt(Instant moment) {
		if (!properties.enabled()) {
			return true;
		}
		var time = moment.atOffset(ZoneOffset.UTC).toLocalTime();
		return !time.isBefore(properties.open()) && time.isBefore(properties.close());
	}

	// throws ConflictException (409) outside the window; the message is shown to the user
	public void requireOpenAt(Instant moment) {
		if (!isOpenAt(moment)) {
			throw new ConflictException(
					"Outside trading hours. Trading is open " + properties.open() + "-" + properties.close() + " UTC");
		}
	}

	public MarketHoursResponse describeAt(Instant moment) {
		var isOpen = isOpenAt(moment);
		return new MarketHoursResponse(isOpen, properties.open().toString(), properties.close().toString(),
				properties.enabled() ? nextChangeAt(moment, isOpen) : null);
	}

	private Instant nextChangeAt(Instant moment, boolean isOpen) {
		var today = LocalDate.ofInstant(moment, ZoneOffset.UTC);
		if (isOpen) {
			return today.atTime(properties.close()).toInstant(ZoneOffset.UTC);
		}
		var opensToday = today.atTime(properties.open()).toInstant(ZoneOffset.UTC);
		return moment.isBefore(opensToday) ? opensToday : opensToday.plusSeconds(24 * 60 * 60);
	}
}
