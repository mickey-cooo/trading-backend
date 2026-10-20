package trading.demo.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.RefreshTokenEntity;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
	// row lock: refresh and logout on one token take turns, so a family revoke never misses a rotated token
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

	// returns 0 when already revoked, so two concurrent refreshes cannot both win
	@Modifying
	@Query("update RefreshTokenEntity t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
	int revoke(@Param("id") UUID id, @Param("now") Instant now);

	@Modifying
	@Query("update RefreshTokenEntity t set t.revokedAt = :now where t.familyId = :family and t.revokedAt is null")
	int revokeFamily(@Param("family") UUID family, @Param("now") Instant now);

	// expired rows only: revoked rows must stay until expiry so a replayed token still revokes its family
	@Modifying
	@Query("delete from RefreshTokenEntity t where t.expiresAt < :now")
	int purgeExpired(@Param("now") Instant now);
}
