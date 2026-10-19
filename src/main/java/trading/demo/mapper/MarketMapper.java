package trading.demo.mapper;

import org.springframework.stereotype.Component;

import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.model.entity.InstrumentEntity;
import trading.demo.model.entity.QuoteEntity;

@Component
public class MarketMapper {
	public MarketResponse toMarketResponse(InstrumentEntity instrument) {
		// DB keeps 18 decimals; strip zeros to show "0.00000001"
		var quantityIncrement = instrument.getQuantityIncrement().stripTrailingZeros().toPlainString();
		var priceIncrement = instrument.getPriceIncrement().stripTrailingZeros().toPlainString();
		return new MarketResponse(instrument.getSymbol(), instrument.getBaseAsset().getSymbol(),
				instrument.getQuoteAsset().getSymbol(), instrument.getStatus(), quantityIncrement, priceIncrement);
	}

	public QuoteResponse toQuoteResponse(QuoteEntity quote) {
		return new QuoteResponse(quote.getInstrument().getSymbol(), quote.getPrice().toPlainString(),
				quote.getVersion(), quote.getCreatedAt());
	}
}
