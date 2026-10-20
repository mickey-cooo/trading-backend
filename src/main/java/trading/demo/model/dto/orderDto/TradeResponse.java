package trading.demo.model.dto.orderDto;

import java.time.Instant;
import java.util.UUID;

// One fill. A market order fills at the price of the quote with this quoteVersion.
public record TradeResponse(UUID orderId, String price, String quantity, long quoteVersion, Instant timestamp) {
}
