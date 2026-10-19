package trading.demo.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Market number rules (app.market.*); limits match the DB column scales so nothing is silently rounded.
@Validated
@ConfigurationProperties(prefix = "app.market")
public record MarketProperties(
		// decimals allowed in a price; a longer price is rejected, never rounded
		@Min(0) @Max(2) int priceDecimals,
		// decimals allowed in an order quantity
		@Min(0) @Max(18) int quantityDecimals,
		// digits allowed before the dot, far above any real price or quantity
		@Min(1) @Max(20) int maxDigitsBeforeDot) {
}
