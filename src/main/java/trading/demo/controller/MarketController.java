package trading.demo.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import trading.demo.model.dto.marketDto.CandleResponse;
import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.service.MarketService;
import trading.demo.service.QuoteStream;

// Read-only and public (see SecurityConfig), so a chart page works before login.
@RestController
@Validated
@RequestMapping("/markets")
public class MarketController {
	private final MarketService marketService;
	private final QuoteStream quoteStream;

	public MarketController(MarketService marketService, QuoteStream quoteStream) {
		this.marketService = marketService;
		this.quoteStream = quoteStream;
	}

	@GetMapping
	public List<MarketResponse> listMarkets() {
		return marketService.listMarkets();
	}

	// current price; stays current until the next quote, 404 before the first
	@GetMapping("/{pair}/quote")
	public QuoteResponse getCurrentQuote(@PathVariable @Size(max = 41) String pair) {
		return marketService.getCurrentQuote(pair);
	}

	// chart data, oldest first; interval 1m|5m|1h, limit 1-500
	@GetMapping("/{pair}/candles")
	public List<CandleResponse> getCandles(@PathVariable @Size(max = 41) String pair,
			@RequestParam(defaultValue = "1m") String interval,
			@RequestParam(defaultValue = "300") @Min(1) @Max(500) int limit) {
		return marketService.getCandles(pair, interval, limit);
	}

	// live candle stream (SSE); public because EventSource cannot send a JWT
	@GetMapping(path = "/{pair}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@PathVariable @Size(max = 41) String pair,
			@RequestParam(defaultValue = "1m") String interval) {
		return quoteStream.subscribe(pair, interval);
	}
}
