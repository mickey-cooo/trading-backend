package trading.demo.model.dto.accountDto;

// One asset in the portfolio. valueUsdt is null when the asset has no price yet.
public record PortfolioAssetResponse(String asset, String available, String reserved, String valueUsdt) {
}
