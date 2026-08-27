package trading.demo.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import trading.demo.model.enums.Common;
import trading.demo.model.enums.UserType;

@Entity
@Getter
@Setter
@Table(name = "user")
public class UserEntity extends TemplateEntity {

	@Column(nullable = false)
	private String firstName;

	@Column(nullable = false)
	private String lastName;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Common status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserType userType;

	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private AddressEntity address;
}
