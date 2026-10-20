package trading.demo.service;

import java.util.UUID;

import trading.demo.model.dto.accountDto.PortfolioResponse;

public interface PortfolioService {
	// What the user holds per asset and its USDT value at the current quotes; reserved counts toward the value.
	PortfolioResponse getPortfolio(UUID userId);
}
