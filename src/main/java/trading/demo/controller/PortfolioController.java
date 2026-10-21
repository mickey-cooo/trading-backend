package trading.demo.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.accountDto.PortfolioResponse;
import trading.demo.model.entity.UserEntity;
import trading.demo.service.PortfolioService;

// The caller's own holdings: the account always comes from the token, never the URL.
@RestController
@RequestMapping("/portfolio")
public class PortfolioController {
	private final PortfolioService portfolioService;

	public PortfolioController(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	@GetMapping
	public PortfolioResponse getPortfolio(@AuthenticationPrincipal UserEntity me) {
		return portfolioService.getPortfolio(me.getId());
	}
}
