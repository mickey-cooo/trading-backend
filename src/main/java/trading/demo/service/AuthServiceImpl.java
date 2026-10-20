package trading.demo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import trading.demo.exception.InvalidTokenException;
import trading.demo.model.dto.userDto.AuthTokensResponse;
import trading.demo.model.dto.userDto.LoginDto;
import trading.demo.model.entity.RefreshTokenEntity;
import trading.demo.model.entity.UserEntity;
import trading.demo.repository.RefreshTokenRepository;
import trading.demo.security.JwtTokenProvider;

@Service
public class AuthServiceImpl implements AuthService {
	private static final Duration REFRESH_TTL = Duration.ofDays(7);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final AuthenticationManager authenticationManager;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenRepository refreshTokenRepository;

	public AuthServiceImpl(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider,
			RefreshTokenRepository refreshTokenRepository) {
		this.authenticationManager = authenticationManager;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	// not @Transactional: Argon2 verification must not pin a DB connection; save() is transactional itself
	@Override
	public AuthTokensResponse login(LoginDto request) {
		var authentication = authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
		return issue((UserEntity) authentication.getPrincipal(), UUID.randomUUID());
	}

	// noRollbackFor: the family revocation on replay must commit despite the exception
	@Override
	@Transactional(noRollbackFor = InvalidTokenException.class)
	public AuthTokensResponse refresh(String refreshToken) {
		var now = Instant.now();
		var token = refreshTokenRepository.findByTokenHash(hash(refreshToken)).orElseThrow(InvalidTokenException::new);
		if (refreshTokenRepository.revoke(token.getId(), now) == 0) {
			// already used: treat as theft, kill every token from this login
			refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
			throw new InvalidTokenException();
		}
		var user = token.getUser();
		if (token.getExpiresAt().isBefore(now) || !user.isEnabled()) {
			throw new InvalidTokenException();
		}
		return issue(user, token.getFamilyId());
	}

	@Override
	@Transactional
	public void logout(String refreshToken) {
		// revoke the whole login so a rotated token still ends the session; unknown tokens are not an error
		refreshTokenRepository.findByTokenHash(hash(refreshToken))
				.ifPresent(t -> refreshTokenRepository.revokeFamily(t.getFamilyId(), Instant.now()));
	}

	private AuthTokensResponse issue(UserEntity user, UUID familyId) {
		var bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		var token = new RefreshTokenEntity();
		token.setUser(user);
		token.setTokenHash(hash(raw));
		token.setFamilyId(familyId);
		token.setExpiresAt(Instant.now().plus(REFRESH_TTL));
		refreshTokenRepository.save(token);

		return new AuthTokensResponse(jwtTokenProvider.generateToken(user), raw, jwtTokenProvider.getExpirationTime());
	}

	static String hash(String raw) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
