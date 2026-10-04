package trading.demo.model.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import trading.demo.model.enums.Common;
import trading.demo.model.enums.UserType;
import trading.demo.model.enums.RoleType;

@Entity
@Getter
@Setter
@Table(name = "users")
public class UserEntity extends TemplateEntity implements UserDetails {

	@Size(max = 100)
	@Column(name = "first_name", length = 100)
	private String firstName;

	@Size(max = 100)
	@Column(name = "last_name", length = 100)
	private String lastName;

	@NotBlank
	@Email
	@Size(max = 254)
	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@JsonIgnore
	@NotBlank
	@Size(max = 255)
	@Column(name = "password", nullable = false, length = 255)
	private String password;

	@Enumerated(EnumType.STRING)
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	@Column(nullable = false, length = 20)
	private Common status = Common.ACTIVE;

	@Enumerated(EnumType.STRING)
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	@Column(nullable = false, length = 20)
	private RoleType role = RoleType.USER;

	@Enumerated(EnumType.STRING)
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	@Column(name = "user_type", nullable = false, length = 20)
	private UserType userType = UserType.TRADER;

	@JsonIgnore
	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private List<AddressEntity> addresses = new ArrayList<>();

	public void setEmail(String email) {
		this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	@JsonIgnore
	@Override
	public String getUsername() {
		return email;
	}

	@JsonIgnore
	@Override
	public boolean isEnabled() {
		return status == Common.ACTIVE;
	}

	@JsonIgnore
	@Override
	public List<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}
}
