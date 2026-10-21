package trading.demo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.adminDto.ChangeStatusRequest;
import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.PublishQuoteRequest;
import trading.demo.model.entity.UserEntity;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.service.AdminService;

// Only an ADMIN can set a price or switch a market on and off.
@RestController
@Validated
@RequestMapping("/admin/markets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketController {
	private final AdminService adminService;

	public AdminMarketController(AdminService adminService) {
		this.adminService = adminService;
	}

	@PostMapping("/{pair}/quotes")
	public ResponseEntity<QuoteResponse> publishQuote(@AuthenticationPrincipal UserEntity admin,
			@PathVariable @Size(max = 41) String pair, @Valid @RequestBody PublishQuoteRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(adminService.publishQuote(admin.getId(), pair, request.price()));
	}

	@PatchMapping("/{pair}/status")
	public MarketResponse changeStatus(@AuthenticationPrincipal UserEntity admin,
			@PathVariable @Size(max = 41) String pair, @Valid @RequestBody ChangeStatusRequest request) {
		return adminService.changeMarketStatus(admin.getId(), pair, request.status());
	}
}
