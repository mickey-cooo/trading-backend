package trading.demo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.AccountEntity;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
	Optional<AccountEntity> findByUserId(UUID userId);

	Optional<AccountEntity> findBySystemSlot(Boolean systemSlot);
}
