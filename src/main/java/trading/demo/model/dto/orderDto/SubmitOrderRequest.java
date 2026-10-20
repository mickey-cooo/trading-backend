package trading.demo.model.dto.orderDto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import trading.demo.model.enums.OrderSide;
import trading.demo.model.enums.OrderType;

// quantity is in the base coin, e.g. 0.01 BTC for BTC-USDT.
public record SubmitOrderRequest(@NotBlank @Size(max = 41) String symbol, @NotNull OrderSide side,
		@NotNull OrderType type, @NotNull BigDecimal quantity) {
}
