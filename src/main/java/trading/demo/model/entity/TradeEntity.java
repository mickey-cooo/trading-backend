package trading.demo.model.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

// One fill of an order, at the price of one quote. Rows are only added.
@Entity
@Immutable
@Getter
@Setter
@Table(name = "trades")
public class TradeEntity extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private OrderEntity order;

	@Column(nullable = false, precision = 38, scale = 18)
	private BigDecimal price;

	@Column(nullable = false, precision = 38, scale = 18)
	private BigDecimal quantity;

	// which quote version this filled at
	@Column(name = "quote_version", nullable = false)
	private long quoteVersion;
}
