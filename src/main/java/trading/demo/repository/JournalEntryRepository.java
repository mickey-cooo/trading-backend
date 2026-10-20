package trading.demo.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.JournalEntryEntity;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {
	boolean existsByReference(String reference);

	@EntityGraph(attributePaths = "asset")
	Page<JournalEntryEntity> findByAccountId(UUID accountId, Pageable pageable);
}
