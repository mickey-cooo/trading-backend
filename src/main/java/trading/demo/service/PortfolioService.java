package trading.demo.service;

import java.util.UUID;

import trading.demo.model.dto.accountDto.PortfolioResponse;

public interface PortfolioService {
	// What the user holds per asset and what it is worth in USDT at the current
	// quotes. Reserved amounts count toward the value.
	PortfolioResponse getPortfolio(UUID userId);
}
