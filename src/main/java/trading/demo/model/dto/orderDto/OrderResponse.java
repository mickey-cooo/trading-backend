package trading.demo.model.dto.orderDto;

import java.time.Instant;
import java.util.UUID;

import trading.demo.model.enums.OrderSide;
import trading.demo.model.enums.OrderStatus;
import trading.demo.model.enums.OrderType;

// quantity is a plain decimal string, never a float.
public record OrderResponse(UUID id, String symbol, OrderSide side, OrderType type, String quantity, OrderStatus status,
		Instant createdAt) {
}
