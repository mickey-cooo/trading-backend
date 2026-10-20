package trading.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

import org.springframework.stereotype.Service;

import trading.demo.config.MarketProperties;
import trading.demo.exception.ResourceNotFoundException;
import trading.demo.model.dto.accountDto.PortfolioAssetResponse;
import trading.demo.model.dto.accountDto.PortfolioResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;

// Not @Transactional, same as ledgerService.balances(): opening an account uses its own transaction.
@Service
public class PortfolioServiceImpl implements PortfolioService {
	// every asset is valued in this one; its own value is the amount
	private static final String VALUE_ASSET = "USDT";

	private final LedgerService ledgerService;
	private final MarketService marketService;
	private final MarketProperties marketProperties;

	public PortfolioServiceImpl(LedgerService ledgerService, MarketService marketService,
			MarketProperties marketProperties) {
		this.ledgerService = ledgerService;
		this.marketService = marketService;
		this.marketProperties = marketProperties;
	}

	@Override
	public PortfolioResponse getPortfolio(UUID userId) {
		var assets = new ArrayList<PortfolioAssetResponse>();
		var quoteTimes = new ArrayList<Instant>();
		for (var balance : ledgerService.balances(userId)) {
			var available = new BigDecimal(balance.available());
			var reserved = new BigDecimal(balance.reserved());
			var total = available.add(reserved);
			String valueUsdt = null;
			if (balance.asset().equals(VALUE_ASSET)) {
				valueUsdt = format(total);
			} else {
				var quote = findCurrentQuote(balance.asset() + "-" + VALUE_ASSET);
				if (quote != null) {
					valueUsdt = format(total.multiply(new BigDecimal(quote.price())));
					quoteTimes.add(quote.timestamp());
				}
			}
			assets.add(new PortfolioAssetResponse(balance.asset(), balance.available(), balance.reserved(), valueUsdt));
		}
		var newestQuoteTime = quoteTimes.stream().max(Comparator.naturalOrder()).orElse(null);
		return new PortfolioResponse(newestQuoteTime, assets);
	}

	// null when the pair has no market or no price yet
	private QuoteResponse findCurrentQuote(String pair) {
		try {
			return marketService.getCurrentQuote(pair);
		} catch (ResourceNotFoundException noQuote) {
			return null;
		}
	}

	private String format(BigDecimal value) {
		return value.setScale(marketProperties.priceDecimals(), RoundingMode.HALF_UP).toPlainString();
	}
}
