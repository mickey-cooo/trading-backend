package trading.demo.controller;

import java.util.List;

import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.service.MarketService;

// Read-only and public (see SecurityConfig), so a chart page works before login.
@RestController
@Validated
@RequestMapping("/api/v1/markets")
public class MarketController {
	private final MarketService marketService;

	public MarketController(MarketService marketService) {
		this.marketService = marketService;
	}

	@GetMapping
	public List<MarketResponse> listMarkets() {
		return marketService.listMarkets();
	}

	// current price; stays current until the next quote, 404 before the first
	@GetMapping("/{symbol}/quote")
	public QuoteResponse getCurrentQuote(@PathVariable @Size(max = 41) String symbol) {
		return marketService.getCurrentQuote(symbol);
	}
}
