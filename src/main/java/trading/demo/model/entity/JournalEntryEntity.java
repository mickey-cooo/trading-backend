package trading.demo.model.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import trading.demo.model.enums.BalanceBucket;
import trading.demo.model.enums.LedgerType;

// One line of the ledger. Rows are only added, never changed or deleted. Every movement is two or more
// lines that add up to zero per asset; adding up one account's lines gives its balance.
@Entity
@Immutable
@Getter
@Setter
@Table(name = "journal_entries", uniqueConstraints = @UniqueConstraint(name = "uk_journal_reference_line", columnNames = {
		"reference", "account_id", "asset_id",
		"bucket"}), indexes = @Index(name = "idx_journal_account_created_at", columnList = "account_id, created_at"))
public class JournalEntryEntity extends TemplateEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private AccountEntity account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private AssetEntity asset;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BalanceBucket bucket;

	// signed change of that bucket
	@Column(nullable = false, precision = 38, scale = 18)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LedgerType type;

	// names the movement, e.g. "funding:<accountId>"; posting the same one twice
	// does nothing
	@Column(nullable = false, length = 100)
	private String reference;
}
