package trading.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import trading.demo.exception.InvalidTokenException;
import trading.demo.model.dto.userDto.LoginDto;
import trading.demo.model.entity.RefreshTokenEntity;
import trading.demo.model.entity.UserEntity;
import trading.demo.model.enums.Common;
import trading.demo.repository.RefreshTokenRepository;
import trading.demo.security.JwtTokenProvider;

class AuthServiceImplTest {
	private final List<RefreshTokenEntity> store = new ArrayList<>();
	private final UserEntity user = new UserEntity();
	private final AuthServiceImpl service;

	AuthServiceImplTest() {
		user.setId(UUID.randomUUID());
		user.setEmail("bob@example.com");
		var jwt = new JwtTokenProvider();
		ReflectionTestUtils.setField(jwt, "secretKey", Base64.getEncoder().encodeToString(new byte[32]));
		ReflectionTestUtils.setField(jwt, "jwtExpiration", 900_000L);
		var repository = (RefreshTokenRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{RefreshTokenRepository.class}, (proxy, method, a) -> switch (method.getName()) {
					case "save" -> {
						var t = (RefreshTokenEntity) a[0];
						t.setId(UUID.randomUUID());
						store.add(t);
						yield t;
					}
					case "findByTokenHash" -> store.stream().filter(t -> t.getTokenHash().equals(a[0])).findFirst();
					case "revoke" -> revokeWhere(t -> t.getId().equals(a[0]), (Instant) a[1]);
					case "revokeFamily" -> revokeWhere(t -> t.getFamilyId().equals(a[0]), (Instant) a[1]);
					default -> throw new UnsupportedOperationException(method.getName());
				});
		service = new AuthServiceImpl(auth -> new UsernamePasswordAuthenticationToken(user, null), jwt, repository);
	}

	private int revokeWhere(java.util.function.Predicate<RefreshTokenEntity> match, Instant now) {
		var n = 0;
		for (var t : store) {
			if (match.test(t) && t.getRevokedAt() == null) {
				t.setRevokedAt(now);
				n++;
			}
		}
		return n;
	}

	private String login() {
		var request = new LoginDto();
		request.setEmail("bob@example.com");
		request.setPassword("x");
		return service.login(request).refreshToken();
	}

	@Test
	void storesOnlyTheHashAndRotatesOnRefresh() {
		var first = login();
		assertEquals(1, store.size());
		assertNotEquals(first, store.get(0).getTokenHash());

		var next = service.refresh(first);

		assertNotNull(next.accessToken());
		assertNotEquals(first, next.refreshToken());
		assertEquals(2, store.size());
		assertNotNull(store.get(0).getRevokedAt());
		assertNull(store.get(1).getRevokedAt());
		assertEquals(store.get(0).getFamilyId(), store.get(1).getFamilyId());
	}

	@Test
	void reusedTokenRevokesWholeFamily() {
		var first = login();
		var next = service.refresh(first).refreshToken();

		assertThrows(InvalidTokenException.class, () -> service.refresh(first));

		assertThrows(InvalidTokenException.class, () -> service.refresh(next));
		store.forEach(t -> assertNotNull(t.getRevokedAt()));
	}

	@Test
	void rejectsUnknownExpiredAndSuspended() {
		assertThrows(InvalidTokenException.class, () -> service.refresh("nope"));

		var expired = login();
		store.get(0).setExpiresAt(Instant.now().minusSeconds(1));
		assertThrows(InvalidTokenException.class, () -> service.refresh(expired));

		var suspended = login();
		user.setStatus(Common.SUSPENDED);
		assertThrows(InvalidTokenException.class, () -> service.refresh(suspended));
	}

	@Test
	void logoutRevokesWholeFamilyEvenWithRotatedToken() {
		var old = login();
		var live = service.refresh(old).refreshToken();

		service.logout(old);
		service.logout("nope");

		store.forEach(t -> assertNotNull(t.getRevokedAt()));
		assertThrows(InvalidTokenException.class, () -> service.refresh(live));
	}
}
