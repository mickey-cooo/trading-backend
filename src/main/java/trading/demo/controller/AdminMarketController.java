package trading.demo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.marketDto.PublishQuoteRequest;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.service.MarketService;

// Only an ADMIN can set a price.
@RestController
@Validated
@RequestMapping("/api/v1/admin/markets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketController {
	private final MarketService marketService;

	public AdminMarketController(MarketService marketService) {
		this.marketService = marketService;
	}

	@PostMapping("/{pair}/quotes")
	public ResponseEntity<QuoteResponse> publishQuote(@PathVariable @Size(max = 41) String pair,
			@Valid @RequestBody PublishQuoteRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(marketService.publishQuote(pair, request.price()));
	}
}
