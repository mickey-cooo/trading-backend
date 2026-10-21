package trading.demo.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
	// Only the :limit newest slots are scanned, counted back from the slot of the newest quote (so an idle market keeps
	// its last candles) and starting on a slot boundary (so the oldest candle is whole).
	@Query(value = """
			SELECT CAST(floor(extract(epoch FROM created_at) / :seconds) * :seconds AS bigint) AS time,
			       (array_agg(price ORDER BY version))[1] AS open,
			       max(price) AS high,
			       min(price) AS low,
			       (array_agg(price ORDER BY version DESC))[1] AS close
			FROM quotes
			WHERE market_id = :marketId AND created_at >= (
			           SELECT to_timestamp((floor(extract(epoch FROM max(created_at)) / :seconds) - (:limit - 1)) * :seconds)
			           FROM quotes WHERE market_id = :marketId)
			GROUP BY 1
			ORDER BY 1 DESC
			LIMIT :limit
			""", nativeQuery = true)
	List<CandleRow> findCandles(@Param("marketId") UUID marketId, @Param("seconds") long seconds,
			@Param("limit") int limit);

	// the newest quote of each market stays whatever its age: it is the current price
	@Modifying
	@Query(value = """
			DELETE FROM quotes q
			WHERE q.created_at < :cutoff
			  AND q.version < (SELECT max(l.version) FROM quotes l WHERE l.market_id = q.market_id)
			""", nativeQuery = true)
	int purgeOlderThan(@Param("cutoff") Instant cutoff);

	// Plain SQL because @PrePersist stamps every saved entity with now; only the demo history seeder needs a past time.
	@Modifying
	@Query(value = """
			INSERT INTO quotes (id, market_id, price, version, created_at, updated_at)
			VALUES (:id, :marketId, :price, :version, :at, :at)
			""", nativeQuery = true)
	void insertAt(@Param("id") UUID id, @Param("marketId") UUID marketId, @Param("price") BigDecimal price,
			@Param("version") long version, @Param("at") Instant at);

	interface CandleRow {
		long getTime();

		BigDecimal getOpen();

		BigDecimal getHigh();

		BigDecimal getLow();

		BigDecimal getClose();
	}
}
