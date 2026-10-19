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

import trading.demo.model.entity.MarketEntity;

@Repository
public interface MarketRepository extends JpaRepository<MarketEntity, UUID> {
	boolean existsByPair(String pair);

	Optional<MarketEntity> findByPair(String pair);

	// fetch both assets in one query, not 1 + 2N queries
	@EntityGraph(attributePaths = {"baseAsset", "quoteAsset"})
	List<MarketEntity> findAllByOrderByPairAsc();

	// row lock: two publishes cannot pick the same version
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from MarketEntity m where m.pair = :pair")
	Optional<MarketEntity> findByPairLockedForWrite(@Param("pair") String pair);
}
