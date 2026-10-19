package trading.demo.model.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

// One price of a pair. Rows are only added; the highest version is current.
@Entity
@Getter
@Setter
@Table(name = "quotes", uniqueConstraints = @UniqueConstraint(name = "uk_quotes_instrument_version", columnNames = {
		"instrument_id", "version"}))
public class QuoteEntity extends TemplateEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "instrument_id", nullable = false)
	private InstrumentEntity instrument;

	// 2 decimals, always above zero (checked in MarketServiceImpl)
	@Column(nullable = false, precision = 38, scale = 2)
	private BigDecimal price;

	// 1 for the first quote of an instrument, then +1 for each new quote
	@Column(nullable = false)
	private long version;
}
