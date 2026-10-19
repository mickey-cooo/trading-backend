package trading.demo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import trading.demo.model.entity.MarketEntity;
import trading.demo.model.entity.QuoteEntity;

@Repository
public interface QuoteRepository extends JpaRepository<QuoteEntity, UUID> {
	// the quote with the highest version is the current price
	Optional<QuoteEntity> findFirstByMarketOrderByVersionDesc(MarketEntity market);
}
