package trading.demo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.AssetEntity;

@Repository
public interface AssetRepository extends JpaRepository<AssetEntity, UUID> {
	Optional<AssetEntity> findBySymbol(String symbol);
}
