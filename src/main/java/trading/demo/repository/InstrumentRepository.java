package trading.demo.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.InstrumentEntity;

@Repository
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, UUID> {
	boolean existsBySymbol(String symbol);

	Optional<InstrumentEntity> findBySymbol(String symbol);

	// fetch both assets in one query, not 1 + 2N queries
	@EntityGraph(attributePaths = {"baseAsset", "quoteAsset"})
	List<InstrumentEntity> findAllByOrderBySymbolAsc();

	// row lock: two publishes cannot pick the same version
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from InstrumentEntity i where i.symbol = :symbol")
	Optional<InstrumentEntity> findBySymbolForUpdate(@Param("symbol") String symbol);
}
