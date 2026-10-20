package trading.demo.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.MarketEntity;
import trading.demo.model.entity.QuoteEntity;

@Repository
public interface QuoteRepository extends JpaRepository<QuoteEntity, UUID> {
	// the quote with the highest version is the current price
	Optional<QuoteEntity> findFirstByMarketOrderByVersionDesc(MarketEntity market);

	// Newest candles first. Quotes group into :seconds slots; open/close = lowest/highest version. Plain SQL.
	@Query(value = """
			SELECT CAST(floor(extract(epoch FROM created_at) / :seconds) * :seconds AS bigint) AS time,
			       (array_agg(price ORDER BY version))[1] AS open,
			       max(price) AS high,
			       min(price) AS low,
			       (array_agg(price ORDER BY version DESC))[1] AS close
			FROM quotes
			WHERE market_id = :marketId
			GROUP BY 1
			ORDER BY 1 DESC
			LIMIT :limit
			""", nativeQuery = true)
	List<CandleRow> findCandles(@Param("marketId") UUID marketId, @Param("seconds") long seconds,
			@Param("limit") int limit);

	interface CandleRow {
		long getTime();

		BigDecimal getOpen();

		BigDecimal getHigh();

		BigDecimal getLow();

		BigDecimal getClose();
	}
}
