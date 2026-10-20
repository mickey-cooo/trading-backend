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
import trading.demo.model.enums.OrderSide;
import trading.demo.model.enums.OrderStatus;
import trading.demo.model.enums.OrderType;

// One buy or sell request. The same user and idempotency key can only ever make one order.
@Entity
@Getter
@Setter
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(name = "uk_orders_user_idempotency_key", columnNames = {
		"user_id",
		"idempotency_key"}), indexes = @Index(name = "idx_orders_user_created_at", columnList = "user_id, created_at"))
public class OrderEntity extends TemplateEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "market_id", nullable = false)
	private MarketEntity market;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private OrderSide side;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private OrderType type;

	// coin amount, at most the allowed decimals (checked before saving)
	@Column(nullable = false, precision = 38, scale = 18)
	private BigDecimal quantity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(name = "idempotency_key", nullable = false, length = 64)
	private String idempotencyKey;

	// SHA-256 of the request, to tell a retry from a different request using the
	// same key
	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;
}
