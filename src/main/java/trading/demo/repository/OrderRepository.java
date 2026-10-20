package trading.demo.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.OrderEntity;

// Every lookup takes the user id, so one user can never reach another's order.
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
	@EntityGraph(attributePaths = "market")
	Optional<OrderEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

	@EntityGraph(attributePaths = "market")
	Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);

	// for the matching job, which has no user
	@EntityGraph(attributePaths = "market")
	Optional<OrderEntity> findWithMarketById(UUID id);

	// oldest first
	@Query("select o.id from OrderEntity o where o.status = trading.demo.model.enums.OrderStatus.OPEN order by o.createdAt, o.id")
	List<UUID> findOpenOrderIds();

	@EntityGraph(attributePaths = "market")
	Page<OrderEntity> findByUserId(UUID userId, Pageable pageable);
}
