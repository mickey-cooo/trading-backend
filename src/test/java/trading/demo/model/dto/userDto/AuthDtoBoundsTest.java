package trading.demo.model.dto.userDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AuthDtoBoundsTest {
	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	private static RegisterDTO register(String email, String password, String confirm) {
		var dto = new RegisterDTO();
		dto.setEmail(email);
		dto.setPassword(password);
		dto.setConfirmPassword(confirm);
		return dto;
	}

	@Test
	void registerAcceptsAsciiEmail() {
		assertTrue(validator.validate(register("bob@example.com", "correct-horse", "correct-horse")).isEmpty());
	}

	@Test
	void registerRejectsNonAsciiLookalikeEmail() {
		var violations = validator.validate(register("adm\u0131n@example.com", "correct-horse", "correct-horse"));

		assertEquals(1, violations.size());
	}

	@Test
	void registerRejectsOversizedConfirmPassword() {
		assertEquals(1, validator.validate(register("bob@example.com", "correct-horse", "x".repeat(73))).size());
	}

	@Test
	void loginRejectsOversizedFields() {
		var dto = new LoginDto();
		dto.setEmail("bob@example.com");
		dto.setPassword("x".repeat(73));
		assertEquals(1, validator.validate(dto).size());
		dto.setPassword("ok-password");
		dto.setEmail("a".repeat(250) + "@b.co");
		assertTrue(validator.validate(dto).stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
	}

	@Test
	void refreshTokenIsBounded() {
		assertTrue(validator.validate(new RefreshTokenRequest("x".repeat(43))).isEmpty());
		assertEquals(1, validator.validate(new RefreshTokenRequest("x".repeat(129))).size());
	}
}
