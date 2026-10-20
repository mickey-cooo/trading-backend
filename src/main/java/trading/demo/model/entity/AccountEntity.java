package trading.demo.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// One paper account per user, plus one system account that is the other side of every movement.
@Entity
@Getter
@Setter
@Table(name = "paper_accounts")
public class AccountEntity extends TemplateEntity {

	// null for the system account
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", unique = true)
	private UserEntity user;

	// TRUE for the system account, null for users: a unique column allows many nulls but only one TRUE
	@Column(name = "system_slot", unique = true)
	private Boolean systemSlot;

	public boolean isSystem() {
		return Boolean.TRUE.equals(systemSlot);
	}
}
