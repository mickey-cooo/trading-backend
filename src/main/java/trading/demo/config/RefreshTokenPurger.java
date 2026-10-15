package trading.demo.config;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import trading.demo.repository.RefreshTokenRepository;

// Rotation inserts a row per refresh and nothing ever deleted them, so the table (and the
// family scan in revokeFamily) grew without bound. Expired rows are useless: drop them.
@Component
public class RefreshTokenPurger {
	private final RefreshTokenRepository refreshTokenRepository;

	public RefreshTokenPurger(RefreshTokenRepository refreshTokenRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT10M")
	@Transactional
	public void purge() {
		refreshTokenRepository.purgeExpired(Instant.now());
	}
}
