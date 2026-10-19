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
import trading.demo.model.enums.RoleType;
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

	// Markets: MarketSeeder creates BTC-USDT and ETH-USDT at startup.
	// BTC-USDT is the pair these tests publish to; ETH-USDT stays without quotes.
	@Test
	void marketsArePublicAndSeeded() throws Exception {
		mockMvc.perform(get("/api/v1/markets")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].pair").value("BTC-USDT")).andExpect(jsonPath("$[0].base").value("BTC"))
				.andExpect(jsonPath("$[0].quote").value("USDT")).andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[0].quantityIncrement").value("0.00000001"))
				.andExpect(jsonPath("$[0].priceIncrement").value("0.01"))
				.andExpect(jsonPath("$[1].pair").value("ETH-USDT"));
	}

	@Test
	void quoteIsNotFoundUntilAPriceIsPublished() throws Exception {
		mockMvc.perform(get("/api/v1/markets/ETH-USDT/quote")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/markets/DOGE-USDT/quote")).andExpect(status().isNotFound());
	}

	@Test
	void adminPublishesQuotesAndEveryoneReadsTheLatest() throws Exception {
		var admin = new UserEntity();
		admin.setEmail("market-admin@example.com");
		admin.setPassword("existing-password-hash");
		admin.setRole(RoleType.ADMIN);
		admin = userRepository.saveAndFlush(admin);

		mockMvc.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").with(user(admin))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"price":"65000.5"}
						""")).andExpect(status().isCreated()).andExpect(jsonPath("$.price").value("65000.50"))
				.andExpect(jsonPath("$.version").value(1));
		// a JSON number works too
		mockMvc.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").with(user(admin))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"price":65100.25}
						""")).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2));

		// public read, no token: the newest quote is the current price
		mockMvc.perform(get("/api/v1/markets/BTC-USDT/quote")).andExpect(status().isOk())
				.andExpect(jsonPath("$.pair").value("BTC-USDT")).andExpect(jsonPath("$.price").value("65100.25"))
				.andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void invalidQuotesAreRejectedAndNormalUsersCannotPublish() throws Exception {
		var admin = new UserEntity();
		admin.setEmail("market-admin-2@example.com");
		admin.setPassword("existing-password-hash");
		admin.setRole(RoleType.ADMIN);
		admin = userRepository.saveAndFlush(admin);
		var normalUser = new UserEntity();
		normalUser.setEmail("market-user@example.com");
		normalUser.setPassword("existing-password-hash");
		normalUser = userRepository.saveAndFlush(normalUser);

		for (var badBody : new String[]{"""
				{"price":"0"}""", """
				{"price":"-5"}""", """
				{"price":"1.005"}""", """
				{}""", """
				{"price":"abc"}"""}) {
			mockMvc.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").with(user(admin))
					.contentType(MediaType.APPLICATION_JSON).content(badBody)).andExpect(status().isBadRequest());
		}
		mockMvc.perform(post("/api/v1/admin/markets/DOGE-USDT/quotes").with(user(admin))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"price":"1"}
						""")).andExpect(status().isNotFound());

		var validBody = """
				{"price":"1"}
				""";
		mockMvc.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").with(user(normalUser))
				.contentType(MediaType.APPLICATION_JSON).content(validBody)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").contentType(MediaType.APPLICATION_JSON)
				.content(validBody)).andExpect(status().isUnauthorized());

		// a normal user is denied before the body is read: 403 even for a broken body
		mockMvc.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").with(user(normalUser))
				.contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isForbidden());
		// absurd exponent must be a clean 400, not an error or a hang
		mockMvc.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").with(user(admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"price\":1E+2147483647}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void publicMarketRoutesIgnoreStaleTokensButOtherMarketRoutesStayPrivate() throws Exception {
		mockMvc.perform(get("/api/v1/markets").header("Authorization", "Bearer stale-token"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/markets/BTC-USDT/quote").header("Authorization", "Bearer stale-token"))
				.andExpect(status().is(org.hamcrest.Matchers.not(401)));
		// only the listed routes are public
		mockMvc.perform(get("/api/v1/markets/BTC-USDT/orders")).andExpect(status().isUnauthorized());
	}
}
