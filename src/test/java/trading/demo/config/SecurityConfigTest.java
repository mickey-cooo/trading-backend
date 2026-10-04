package trading.demo.config;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {
	@Test
	void usesArgon2ForCredentialPasswords() {
		var passwordEncoder = new SecurityConfig().passwordEncoder();
		var rawPassword = "correct horse battery staple";

		var passwordHash = passwordEncoder.encode(rawPassword);

		assertNotEquals(rawPassword, passwordHash);
		assertTrue(passwordHash.startsWith("$argon2id$"));
		assertTrue(passwordEncoder.matches(rawPassword, passwordHash));
	}
}
