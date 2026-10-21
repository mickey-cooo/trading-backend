package trading.demo.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// One coin or currency that can be held: BTC, ETH, USDT.
@Entity
@Getter
@Setter
@Table(name = "assets")
public class AssetEntity extends BaseEntity {

	@Column(nullable = false, unique = true, length = 20)
	private String symbol;

	@Column(nullable = false, length = 100)
	private String name;
}
