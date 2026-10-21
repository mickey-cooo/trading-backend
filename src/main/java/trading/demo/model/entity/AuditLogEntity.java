package trading.demo.model.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import trading.demo.model.enums.AuditAction;

// One admin action. Rows are only ever added, never changed.
@Entity
@Getter
@Setter
@Immutable
@Table(name = "audit_logs")
public class AuditLogEntity extends BaseEntity {

	@Column(name = "admin_id", nullable = false)
	private UUID adminId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private AuditAction action;

	// what was acted on: a market pair or a user id
	@Column(nullable = false, length = 100)
	private String target;

	// the new value, e.g. "SUSPENDED" or "65000.50"
	@Column(length = 200)
	private String detail;
}
