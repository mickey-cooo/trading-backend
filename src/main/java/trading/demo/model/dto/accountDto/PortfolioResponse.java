package trading.demo.model.dto.accountDto;

import java.time.Instant;
import java.util.List;

// quoteTime is the newest quote used to price the assets; null when no asset was priced.
public record PortfolioResponse(Instant quoteTime, List<PortfolioAssetResponse> assets) {
}
