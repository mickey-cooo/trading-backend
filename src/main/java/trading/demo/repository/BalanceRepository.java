package trading.demo.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.BalanceEntity;

@Repository
public interface BalanceRepository extends JpaRepository<BalanceEntity, UUID> {
	@EntityGraph(attributePaths = "asset")
	List<BalanceEntity> findByAccountIdOrderByAssetSymbolAsc(UUID accountId);

	// row locks, always in id order, so two postings cannot wait on each other
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from BalanceEntity b where b.account.id in :accountIds order by b.id")
	List<BalanceEntity> lockByAccountIds(@Param("accountIds") Collection<UUID> accountIds);
}
