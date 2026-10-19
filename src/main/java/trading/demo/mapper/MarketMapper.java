package trading.demo.mapper;

import org.springframework.stereotype.Component;

import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.model.entity.MarketEntity;
import trading.demo.model.entity.QuoteEntity;

@Component
public class MarketMapper {
	public MarketResponse toMarketResponse(MarketEntity market) {
		// DB keeps 18 decimals; strip zeros to show "0.00000001"
		var quantityIncrement = market.getMinQuantityStep().stripTrailingZeros().toPlainString();
		var priceIncrement = market.getMinPriceStep().stripTrailingZeros().toPlainString();
		return new MarketResponse(market.getSymbol(), market.getBaseAsset().getSymbol(),
				market.getQuoteAsset().getSymbol(), market.getStatus(), quantityIncrement, priceIncrement);
	}

	public QuoteResponse toQuoteResponse(QuoteEntity quote) {
		return new QuoteResponse(quote.getMarket().getSymbol(), quote.getPrice().toPlainString(), quote.getVersion(),
				quote.getCreatedAt());
	}
}
