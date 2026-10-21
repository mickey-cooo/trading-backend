package trading.demo.config;

import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

// Daily trading window in UTC (app.market-hours.*). Orders are refused outside it; the window never crosses midnight.
@Validated
@ConfigurationProperties(prefix = "app.market-hours")
public record MarketHoursProperties(
		// false opens the market all day (tests and the flow script use this)
		@DefaultValue("true") boolean enabled, @DefaultValue("20:00") LocalTime open,
		@DefaultValue("23:00") LocalTime close) {

	public MarketHoursProperties {
		if (!open.isBefore(close)) {
			throw new IllegalArgumentException("app.market-hours.open must be before close");
		}
	}
}
