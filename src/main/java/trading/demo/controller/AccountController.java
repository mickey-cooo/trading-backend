package trading.demo.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.accountDto.BalanceResponse;
import trading.demo.model.dto.accountDto.LedgerEntryResponse;
import trading.demo.model.entity.UserEntity;
import trading.demo.service.LedgerService;

// The caller's own money: the account always comes from the token, never the URL.
@RestController
@Validated
@RequestMapping("/api/v1/accounts/me")
public class AccountController {
	private final LedgerService ledgerService;

	public AccountController(LedgerService ledgerService) {
		this.ledgerService = ledgerService;
	}

	@GetMapping("/balances")
	public List<BalanceResponse> getBalances(@AuthenticationPrincipal UserEntity me) {
		return ledgerService.balances(me.getId());
	}

	// newest first; size 1-100, default 20
	@GetMapping("/ledger")
	public PagedModel<LedgerEntryResponse> getLedger(@AuthenticationPrincipal UserEntity me,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(ledgerService.ledger(me.getId(), page, size));
	}
}
