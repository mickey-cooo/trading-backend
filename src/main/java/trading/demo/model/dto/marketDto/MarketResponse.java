package trading.demo.model.dto.marketDto;

import trading.demo.model.enums.Common;

// One trading pair in the market list. Decimals are strings, not floats.
public record MarketResponse(String pair, String base, String quote, Common status, String quantityIncrement,
		String priceIncrement) {
}
