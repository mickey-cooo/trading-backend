package trading.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;

import com.jayway.jsonpath.JsonPath;
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
		var owner = user;

		mockMvc.perform(
				post("/api/v1/users/me/profile").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content("""
						{"firstName":" Alice ","lastName":" Example "}
						""")).andExpect(status().isCreated()).andExpect(jsonPath("$.firstName").value("Alice"))
				.andExpect(jsonPath("$.lastName").value("Example")).andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());

		var savedUser = userRepository.findById(user.getId()).orElseThrow();
		assertEquals("alice@example.com", savedUser.getEmail());
		assertEquals("existing-password-hash", savedUser.getPassword());
		assertNotNull(savedUser.getCreatedAt());

		mockMvc.perform(
				post("/api/v1/users/me/profile").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content("""
						{"firstName":"Alice","lastName":"Example","email":"changed@example.com","password":"changed"}
						""")).andExpect(status().isBadRequest());
	}

	@Test
	void registerLoginRefreshLogout() throws Exception {
		var signUp = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"bob@example.com","password":"correct-horse","confirmPassword":"correct-horse"}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist()).andReturn();
		String id = JsonPath.read(signUp.getResponse().getContentAsString(), "$.id");

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"bob@example.com","password":"correct-horse","confirmPassword":"correct-horse"}
				""")).andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"carol@example.com","password":"correct-horse","confirmPassword":"different"}
				""")).andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"bob@example.com","password":"wrong-password"}
				""")).andExpect(status().isUnauthorized());
		var login = mockMvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer stale-token")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"bob@example.com","password":"correct-horse"}
						""")).andExpect(status().isOk()).andReturn();
		String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
		String refresh = JsonPath.read(login.getResponse().getContentAsString(), "$.refreshToken");

		mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer stale-token"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id));
		mockMvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"firstName":"Bob"}
						""")).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Bob"));

		// no path carries another user's id for a normal user; admin-only routes are
		// forbidden
		mockMvc.perform(get("/api/v1/users/{id}", id).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/users").param("ids", id).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());

		// refresh rotates; reusing the old token fails and revokes the new one too
		var rotated = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isOk()).andReturn();
		String next = JsonPath.read(rotated.getResponse().getContentAsString(), "$.refreshToken");
		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + next + "\"}")).andExpect(status().isUnauthorized());

		// logout revokes the token
		var again = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"bob@example.com","password":"correct-horse"}
				""")).andExpect(status().isOk()).andReturn();
		String last = JsonPath.read(again.getResponse().getContentAsString(), "$.refreshToken");
		mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + last + "\"}")).andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + last + "\"}")).andExpect(status().isUnauthorized());
	}
}
