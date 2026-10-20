package trading.demo.service;

import java.math.BigDecimal;
import java.util.UUID;

import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.model.dto.userDto.UserResponseDTO;
import trading.demo.model.enums.Common;

// Every admin action goes through here, and each one writes an audit log row in the same transaction.
public interface AdminService {
	QuoteResponse publishQuote(UUID adminId, String pair, BigDecimal price);

	// ACTIVE or SUSPENDED only. A suspended market takes no new orders and no fills; cancel still works.
	MarketResponse changeMarketStatus(UUID adminId, String pair, Common status);

	// ACTIVE or SUSPENDED only. Suspending cancels open orders and releases reserves in one transaction. Not for yourself.
	UserResponseDTO changeUserStatus(UUID adminId, UUID userId, Common status);
}
