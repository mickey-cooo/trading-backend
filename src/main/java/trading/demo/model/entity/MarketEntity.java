package trading.demo.model.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import trading.demo.model.enums.Common;

// One trading pair, e.g. BTC-USDT: you buy or sell the base asset, priced in the quote asset.
@Entity
@Getter
@Setter
@Table(name = "markets")
public class MarketEntity extends TemplateEntity {

	// "BTC-USDT"
	@Column(nullable = false, unique = true, length = 41)
	private String symbol;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "base_asset_id", nullable = false)
	private AssetEntity baseAsset;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "quote_asset_id", nullable = false)
	private AssetEntity quoteAsset;

	// ACTIVE = tradable, SUSPENDED = switched off by an admin
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Common status = Common.ACTIVE;

	// smallest quantity step (enforced when orders arrive)
	@Column(name = "min_quantity_step", nullable = false, precision = 38, scale = 18)
	private BigDecimal minQuantityStep;

	// smallest step a price can move by (stored now, enforced when orders arrive)
	@Column(name = "min_price_step", nullable = false, precision = 38, scale = 18)
	private BigDecimal minPriceStep;
}
