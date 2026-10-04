package trading.demo.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.Locale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
	Optional<UserEntity> findByEmailIgnoreCase(String email);

	default Optional<UserEntity> findByEmail(String email) {
		return email == null ? Optional.empty() : findByEmailIgnoreCase(email.strip().toLowerCase(Locale.ROOT));
	}
}
