package trading.demo.model.dto.marketDto;

import java.time.Instant;

// The current price of one trading pair. price is a plain string ("65000.50"), never a float.
public record QuoteResponse(String pair, String price, long version, Instant timestamp) {
}
