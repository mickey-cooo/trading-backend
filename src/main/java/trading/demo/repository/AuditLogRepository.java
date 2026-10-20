package trading.demo.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.AuditLogEntity;
import trading.demo.model.enums.AuditAction;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
	List<AuditLogEntity> findByActionOrderByCreatedAtAsc(AuditAction action);
}
