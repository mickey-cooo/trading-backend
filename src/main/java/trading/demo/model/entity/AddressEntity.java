package trading.demo.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "user_addresses")
public class AddressEntity extends TemplateEntity {
	@Column(nullable = true)
	private String country;

	@Column(nullable = true)
	private String province;

	@Column(nullable = true)
	private String district;

	@Column(name = "sub_district")
	private String subDistrict;

	@Column(name = "postal_code")
	private String postalCode;

	@Column(nullable = true)
	private String detail;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;
}
