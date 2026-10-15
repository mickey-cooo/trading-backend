package trading.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import trading.demo.model.entity.UserEntity;
import trading.demo.model.enums.RoleType;
import trading.demo.repository.UserRepository;

class AdminBootstrapTest {
	private final AtomicReference<UserEntity> saved = new AtomicReference<>();
	private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

	private AdminBootstrap bootstrap(UserEntity existing, String email, String password) {
		var repository = (UserRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{UserRepository.class}, (proxy, method, a) -> switch (method.getName()) {
					case "findByEmail" -> Optional.ofNullable(existing);
					case "save" -> {
						saved.set((UserEntity) a[0]);
						yield a[0];
					}
					default -> throw new UnsupportedOperationException(method.getName());
				});
		return new AdminBootstrap(repository, encoder, email, password);
	}

	@Test
	void createsHashedAdmin() {
		bootstrap(null, "Admin@Example.com", "correct-horse").run(null);

		assertEquals("admin@example.com", saved.get().getEmail());
		assertEquals(RoleType.ADMIN, saved.get().getRole());
		assertTrue(encoder.matches("correct-horse", saved.get().getPassword()));
	}

	@Test
	void skipsWhenUnsetOrAlreadyExistsAndRejectsShortPassword() {
		bootstrap(null, "", "").run(null);
		bootstrap(new UserEntity(), "admin@example.com", "correct-horse").run(null);
		assertNull(saved.get());

		assertThrows(IllegalStateException.class, () -> bootstrap(null, "admin@example.com", "short").run(null));
	}
}
