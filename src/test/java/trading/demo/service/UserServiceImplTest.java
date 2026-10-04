package trading.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import trading.demo.exception.ValidationException;
import trading.demo.mapper.UserMapper;
import trading.demo.model.dto.userDto.CreateUserProfileRequest;
import trading.demo.model.dto.userDto.RegisterDTO;
import trading.demo.model.entity.UserEntity;
import trading.demo.model.enums.RoleType;
import trading.demo.repository.UserRepository;

class UserServiceImplTest {
	@Test
	void createsProfileWithoutChangingCredentials() {
		var id = UUID.randomUUID();
		var user = new UserEntity();
		user.setId(id);
		user.setEmail("Alice@Example.COM");
		user.setPassword("existing-password-hash");
		user.setRole(null);
		var request = new CreateUserProfileRequest();
		request.setFirstName("Alice");
		request.setLastName("Example");
		var savedUser = new AtomicReference<UserEntity>();
		var repository = (UserRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{UserRepository.class}, (proxy, method, arguments) -> switch (method.getName()) {
					case "findById" -> Optional.of(user);
					case "save" -> {
						savedUser.set((UserEntity) arguments[0]);
						yield arguments[0];
					}
					default -> throw new UnsupportedOperationException(method.getName());
				});

		var response = new UserServiceImpl(repository, new UserMapper(), null).createUserProfile(id, request);

		assertEquals("Alice", savedUser.get().getFirstName());
		assertEquals("Example", savedUser.get().getLastName());
		assertEquals("alice@example.com", savedUser.get().getEmail());
		assertEquals("existing-password-hash", savedUser.get().getPassword());
		assertNotNull(savedUser.get().getCreatedAt());
		assertEquals(RoleType.USER, savedUser.get().getRole());
		assertEquals("Alice", response.getFirstName());
	}

	@Test
	void signUpHashesPasswordAndRejectsMismatch() {
		var savedUser = new AtomicReference<UserEntity>();
		var repository = (UserRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{UserRepository.class}, (proxy, method, arguments) -> {
					savedUser.set((UserEntity) arguments[0]);
					return arguments[0];
				});
		var encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
		var service = new UserServiceImpl(repository, new UserMapper(), encoder);
		var request = new RegisterDTO();
		request.setEmail(" Bob@Example.COM ");
		request.setPassword("correct-horse");
		request.setConfirmPassword("correct-horse");

		service.signUp(request);

		assertEquals("bob@example.com", savedUser.get().getEmail());
		assertTrue(encoder.matches("correct-horse", savedUser.get().getPassword()));
		assertEquals(RoleType.USER, savedUser.get().getRole());

		savedUser.set(null);
		request.setConfirmPassword("different");
		assertThrows(ValidationException.class, () -> service.signUp(request));
		assertEquals(null, savedUser.get());
	}
}
