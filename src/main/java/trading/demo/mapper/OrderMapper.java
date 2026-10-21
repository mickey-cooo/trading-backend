package trading.demo.mapper;

import org.springframework.stereotype.Component;

import trading.demo.config.MarketProperties;
import trading.demo.model.dto.orderDto.OrderResponse;
import trading.demo.model.dto.orderDto.TradeResponse;
import trading.demo.model.entity.TradeEntity;
import trading.demo.model.entity.OrderEntity;

@Component
public class OrderMapper {
	private final MarketProperties marketProperties;

	public OrderMapper(MarketProperties marketProperties) {
		this.marketProperties = marketProperties;
	}

	public OrderResponse toOrderResponse(OrderEntity order) {
		return new OrderResponse(order.getId(), order.getMarket().getPair(), order.getSide(), order.getType(),
				quantity(order.getQuantity()), limitPrice(order), order.getStatus(), order.getCreatedAt());
	}

	public TradeResponse toTradeResponse(TradeEntity trade) {
		return new TradeResponse(trade.getOrder().getId(),
				// the DB keeps 18 decimals; show the market's own, same as a fresh order
				trade.getPrice().setScale(marketProperties.priceDecimals()).toPlainString(),
				quantity(trade.getQuantity()), trade.getQuoteVersion(), trade.getCreatedAt());
	}

	private String limitPrice(OrderEntity order) {
		return order.getLimitPrice() == null
				? null
				: order.getLimitPrice().setScale(marketProperties.priceDecimals()).toPlainString();
	}

	private String quantity(java.math.BigDecimal value) {
		return value.setScale(marketProperties.quantityDecimals()).toPlainString();
	}
}
