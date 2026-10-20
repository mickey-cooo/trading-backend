package trading.demo.model.dto.accountDto;

import java.time.Instant;

import trading.demo.model.enums.BalanceBucket;
import trading.demo.model.enums.LedgerType;

// One ledger line; amount is signed and applies to the bucket.
public record LedgerEntryResponse(String asset, BalanceBucket bucket, String amount, LedgerType type, String reference,
		Instant timestamp) {
}
