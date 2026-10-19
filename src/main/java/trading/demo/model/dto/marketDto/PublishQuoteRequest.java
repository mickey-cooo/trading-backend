package trading.demo.model.dto.marketDto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

// Admin request body: { "price": "65000.50" }. The price rules are checked in MarketServiceImpl.
public record PublishQuoteRequest(@NotNull BigDecimal price) {
}
