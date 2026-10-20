package trading.demo.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.ExecutionEntity;

@Repository
public interface ExecutionRepository extends JpaRepository<ExecutionEntity, UUID> {
	// executions of this user's orders
	Page<ExecutionEntity> findByOrderUserId(UUID userId, Pageable pageable);
}
