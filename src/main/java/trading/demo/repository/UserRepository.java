package trading.demo.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.Locale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
	// exact match on the stored lower-cased email: uses the unique index and can never match two rows
	@Query("select u from UserEntity u where u.email = :email")
	Optional<UserEntity> findByCanonicalEmail(@Param("email") String email);

	default Optional<UserEntity> findByEmail(String email) {
		return email == null ? Optional.empty() : findByCanonicalEmail(email.strip().toLowerCase(Locale.ROOT));
	}
}
