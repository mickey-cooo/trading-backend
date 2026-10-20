package trading.demo.model.dto.orderDto;

import java.time.Instant;
import java.util.UUID;

import trading.demo.model.enums.OrderSide;
import trading.demo.model.enums.OrderStatus;
import trading.demo.model.enums.OrderType;

// quantity and limitPrice are plain decimal strings, never floats. limitPrice is null for a MARKET order.
public record OrderResponse(UUID id, String symbol, OrderSide side, OrderType type, String quantity, String limitPrice,
		OrderStatus status, Instant createdAt) {
}
