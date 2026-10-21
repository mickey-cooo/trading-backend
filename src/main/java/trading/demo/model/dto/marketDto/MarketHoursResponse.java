package trading.demo.model.dto.marketDto;

import java.time.Instant;

// When trading is open, in UTC. nextChange is the moment the market opens (if closed) or closes (if open); null when the market is open all day.
public record MarketHoursResponse(boolean isOpen, String open, String close, Instant nextChange) {
}
