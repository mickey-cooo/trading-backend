package trading.demo.service;

import java.math.BigDecimal;
import java.util.List;

import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;

public interface MarketService {
	// every trading pair, sorted by symbol
	List<MarketResponse> listMarkets();

	// the latest published price of one pair
	QuoteResponse getCurrentQuote(String symbol);

	// save a new price; it stays current until the next one
	QuoteResponse publishQuote(String symbol, BigDecimal price);

	// throws ValidationException unless quantity is above zero, max 8 decimals
	void requireValidQuantity(BigDecimal quantity);
}
