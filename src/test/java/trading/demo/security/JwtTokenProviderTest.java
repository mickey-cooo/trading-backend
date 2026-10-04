package trading.demo.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import trading.demo.model.entity.UserEntity;
import trading.demo.model.enums.Common;

class JwtTokenProviderTest {

	private static JwtTokenProvider provider(long expirationMs) {
		JwtTokenProvider provider = new JwtTokenProvider();
		ReflectionTestUtils.setField(provider, "secretKey", Base64.getEncoder().encodeToString(new byte[32]));
		ReflectionTestUtils.setField(provider, "jwtExpiration", expirationMs);
		return provider;
	}

	private static UserEntity user(String email) {
		UserEntity user = new UserEntity();
		user.setEmail(email);
		return user;
	}

	@Test
	void tokenRoundTripsUsernameAndValidatesForSameUser() {
		JwtTokenProvider provider = provider(60_000);
		UserEntity user = user("trader@example.com");

		String token = provider.generateToken(user);

		assertEquals("trader@example.com", provider.extractUsername(token));
		assertTrue(provider.isTokenValid(token, user));
		assertFalse(provider.isTokenValid(token, user("other@example.com")));
		assertEquals("ROLE_USER", user.getAuthorities().iterator().next().getAuthority());
		assertTrue(user.isEnabled());
		user.setStatus(Common.SUSPENDED);
		assertFalse(user.isEnabled());
	}

	@Test
	void expiredTokenIsRejected() {
		JwtTokenProvider provider = provider(-1_000);
		UserEntity user = user("trader@example.com");

		String token = provider.generateToken(user);

		assertThrows(ExpiredJwtException.class, () -> provider.isTokenValid(token, user));
	}
}
