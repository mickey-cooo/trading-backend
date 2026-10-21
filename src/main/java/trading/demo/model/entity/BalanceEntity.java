package trading.demo.model.entity;

import java.math.BigDecimal;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

// What one account holds of one asset. System accounts have no rows: their side is only in the ledger.
@Entity
@Getter
@Setter
@Table(name = "balances", uniqueConstraints = @UniqueConstraint(name = "uk_balances_account_asset", columnNames = {
		"account_id",
		"asset_id"}), check = @CheckConstraint(name = "ck_balances_not_negative", constraint = "available >= 0 and reserved >= 0"))
public class BalanceEntity extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private AccountEntity account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private AssetEntity asset;

	// free to spend
	@Column(nullable = false, precision = 38, scale = 18)
	private BigDecimal available = BigDecimal.ZERO;

	// held back for open limit orders
	@Column(nullable = false, precision = 38, scale = 18)
	private BigDecimal reserved = BigDecimal.ZERO;
}
