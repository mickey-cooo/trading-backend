package trading.demo.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.TradeEntity;

@Repository
public interface TradeRepository extends JpaRepository<TradeEntity, UUID> {
	// trades of this user's orders
	Page<TradeEntity> findByOrderUserId(UUID userId, Pageable pageable);
}
