package trading.demo.config;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Fake price feed settings (app.simulator.*); off unless enabled.
@Validated
@ConfigurationProperties(prefix = "app.simulator")
public record SimulatorProperties(boolean enabled,
		// fixed seed: the same run gives the same prices
		long seed,
		// size of one tick as a fraction of the price (0.0005 = 0.05%)
		@DecimalMin("0") @DecimalMax("0.1") double volatility,
		// price a market starts from when it has no quote yet; markets not listed here
		// are skipped
		Map<String, BigDecimal> startPrices) {

	public SimulatorProperties {
		startPrices = startPrices == null ? Map.of() : startPrices;
	}
}
