package trading.demo.model.dto.accountDto;

// What one asset is worth to the caller. Amounts are plain decimal strings, never floats.
public record BalanceResponse(String asset, String available, String reserved) {
}
