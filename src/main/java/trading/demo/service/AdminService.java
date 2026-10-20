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

	// ACTIVE or SUSPENDED only. A suspended market takes no new orders and has no
	// fills; cancel still works.
	MarketResponse changeMarketStatus(UUID adminId, String pair, Common status);

	// ACTIVE or SUSPENDED only. Suspending cancels the user's open orders and
	// releases their reserves in the same transaction. An admin cannot change their
	// own status.
	UserResponseDTO changeUserStatus(UUID adminId, UUID userId, Common status);
}
