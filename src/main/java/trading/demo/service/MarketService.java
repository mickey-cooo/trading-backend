package trading.demo.service;

import java.math.BigDecimal;
import java.util.List;

import trading.demo.model.dto.marketDto.CandleResponse;
import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.model.enums.Common;

public interface MarketService {
	// every trading pair, sorted by pair
	List<MarketResponse> listMarkets();

	// the latest published price of one pair
	QuoteResponse getCurrentQuote(String pair);

	// save a new price; it stays current until the next one
	QuoteResponse publishQuote(String pair, BigDecimal price);

	// chart candles, oldest first; interval is 1m, 5m or 1h (else
	// ValidationException)
	List<CandleResponse> getCandles(String pair, String interval, int limit);

	// Switches a pair on (ACTIVE) or off (SUSPENDED). Any other status is a
	// ValidationException.
	MarketResponse changeStatus(String pair, Common status);

	// throws ValidationException unless price is above zero, max 2 decimals
	void requireValidPrice(BigDecimal price);

	// throws ValidationException unless quantity is above zero, max 8 decimals
	void requireValidQuantity(BigDecimal quantity);
}
