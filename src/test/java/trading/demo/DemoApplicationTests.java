package trading.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import trading.demo.model.entity.UserEntity;
import trading.demo.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DemoApplicationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
		registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
		registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("security.jwt.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
	}

	@Test
	void contextLoads() {
	}

	@Test
	void profileApiCreatesOnlyProfileDetails() throws Exception {
		var user = new UserEntity();
		user.setEmail("alice@example.com");
		user.setPassword("existing-password-hash");
		user = userRepository.saveAndFlush(user);

		mockMvc.perform(
				post("/api/v1/users/{id}/profile", user.getId()).contentType(MediaType.APPLICATION_JSON).content("""
						{"firstName":" Alice ","lastName":" Example "}
						""")).andExpect(status().isCreated()).andExpect(jsonPath("$.firstName").value("Alice"))
				.andExpect(jsonPath("$.lastName").value("Example")).andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());

		var savedUser = userRepository.findById(user.getId()).orElseThrow();
		assertEquals("alice@example.com", savedUser.getEmail());
		assertEquals("existing-password-hash", savedUser.getPassword());
		assertNotNull(savedUser.getCreatedAt());

		mockMvc.perform(
				post("/api/v1/users/{id}/profile", user.getId()).contentType(MediaType.APPLICATION_JSON).content("""
						{"firstName":"Alice","lastName":"Example","email":"changed@example.com","password":"changed"}
						""")).andExpect(status().isBadRequest());
	}

}
