package trading.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

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
import trading.demo.exception.InsufficientFundsException;
import trading.demo.model.dto.orderDto.SubmitOrderRequest;
import trading.demo.model.enums.Common;
import trading.demo.model.enums.OrderSide;
import trading.demo.model.enums.OrderType;
import trading.demo.model.entity.LedgerEntryEntity;
import trading.demo.model.entity.UserEntity;
import trading.demo.model.enums.AuditAction;
import trading.demo.model.enums.BalanceBucket;
import trading.demo.model.enums.LedgerType;
import trading.demo.model.enums.RoleType;
import trading.demo.repository.AccountRepository;
import trading.demo.repository.AssetRepository;
import trading.demo.repository.AuditLogRepository;
import trading.demo.repository.BalanceRepository;
import trading.demo.repository.LedgerEntryRepository;
import trading.demo.repository.MarketRepository;
import trading.demo.repository.UserRepository;
import trading.demo.service.LedgerService;
import trading.demo.service.MarketService;
import trading.demo.service.OrderService;
import trading.demo.service.LedgerService.Leg;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DemoApplicationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LedgerService ledgerService;

	@Autowired
	private BalanceRepository balanceRepository;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private OrderService orderService;

	@Autowired
	private MarketService marketService;

	@Autowired
	private MarketRepository marketRepository;

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
		registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
		registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		// tests call matchOpenOrders() themselves, so the one-second job must not race them
		registry.add("app.matcher.enabled", () -> "false");
		// the order tests run at any hour
		registry.add("app.market-hours.enabled", () -> "false");
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

		// no path carries another user's id for a normal user; admin-only routes are forbidden
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

	// Markets: MarketSeeder creates BTC-USDT and ETH-USDT. Tests publish to BTC-USDT only; ETH-USDT has no quotes.
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

		var first = mockMvc
				.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").with(user(admin))
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"price":"65000.5"}
								"""))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.price").value("65000.50")).andReturn();
		// other tests also publish BTC-USDT prices, so versions are compared, not hard-coded
		int firstVersion = JsonPath.read(first.getResponse().getContentAsString(), "$.version");
		// a JSON number works too
		mockMvc.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").with(user(admin))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"price":65100.25}
						""")).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(firstVersion + 1));

		// public read, no token: the newest quote is the current price
		mockMvc.perform(get("/api/v1/markets/BTC-USDT/quote")).andExpect(status().isOk())
				.andExpect(jsonPath("$.pair").value("BTC-USDT")).andExpect(jsonPath("$.price").value("65100.25"))
				.andExpect(jsonPath("$.version").value(firstVersion + 1)).andExpect(jsonPath("$.timestamp").exists());
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

	// Paper accounts and ledger: the first call opens the account and grants 10,000 USDT, once.
	private UserEntity newUser(String email) {
		var user = new UserEntity();
		user.setEmail(email);
		user.setPassword("existing-password-hash");
		return userRepository.saveAndFlush(user);
	}

	@Test
	void balancesAndLedgerOpenTheAccountWithOneFunding() throws Exception {
		var owner = newUser("wallet-owner@example.com");

		for (int call = 0; call < 2; call++) {
			mockMvc.perform(get("/api/v1/accounts/me/balances").with(user(owner))).andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].asset").value("BTC"))
					.andExpect(jsonPath("$[0].available").value("0.000000000000000000"))
					.andExpect(jsonPath("$[2].asset").value("USDT"))
					.andExpect(jsonPath("$[2].available").value("10000.000000000000000000"))
					.andExpect(jsonPath("$[2].reserved").value("0.000000000000000000"));
		}
		mockMvc.perform(get("/api/v1/accounts/me/ledger").with(user(owner))).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].type").value("FUNDING"))
				.andExpect(jsonPath("$.content[0].asset").value("USDT"))
				.andExpect(jsonPath("$.content[0].amount").value("10000.000000000000000000"))
				.andExpect(jsonPath("$.content[0].reference").exists())
				.andExpect(jsonPath("$.page.totalElements").value(1));

		mockMvc.perform(get("/api/v1/accounts/me/ledger").param("size", "101").with(user(owner)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/accounts/me/ledger").param("page", "-1").with(user(owner)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/accounts/me/balances")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/accounts/me/ledger")).andExpect(status().isUnauthorized());
	}

	@Test
	void concurrentFirstCallsFundOnlyOnce() throws Exception {
		var owner = newUser("wallet-race@example.com");
		var accountIds = runTogether(8, () -> ledgerService.findOrOpen(owner.getId()).getId());

		assertEquals(1, accountIds.stream().distinct().count());
		var fundings = ledgerEntryRepository.findAll().stream().filter(
				line -> line.getAccount().getId().equals(accountIds.get(0)) && line.getType() == LedgerType.FUNDING)
				.toList();
		assertEquals(1, fundings.size());
	}

	@Test
	void concurrentSpendsCannotGoBelowZero() throws Exception {
		var owner = newUser("wallet-spender@example.com");
		var account = ledgerService.findOrOpen(owner.getId());
		var usdt = assetRepository.findBySymbol("USDT").orElseThrow();
		var system = accountRepository.findBySystemSlot(true).orElseThrow();

		// two spends of 6,000 from 10,000: exactly one can succeed
		var results = runTogether(2, () -> {
			try {
				return ledgerService.post("spend:" + java.util.UUID.randomUUID(), LedgerType.TRADE,
						List.of(new Leg(account, usdt, BalanceBucket.AVAILABLE, new BigDecimal("-6000")),
								new Leg(system, usdt, BalanceBucket.AVAILABLE, new BigDecimal("6000"))));
			} catch (InsufficientFundsException e) {
				return false;
			}
		});

		assertEquals(1, results.stream().filter(done -> done).count());
		var balance = balanceRepository.findByAccountIdOrderByAssetSymbolAsc(account.getId()).get(2);
		assertEquals(0, new BigDecimal("4000").compareTo(balance.getAvailable()));
	}

	@Test
	void samePostedReferenceChangesNothingTheSecondTime() {
		var owner = newUser("wallet-retry@example.com");
		var account = ledgerService.findOrOpen(owner.getId());
		var usdt = assetRepository.findBySymbol("USDT").orElseThrow();
		var reserve = List.of(new Leg(account, usdt, BalanceBucket.AVAILABLE, new BigDecimal("-100")),
				new Leg(account, usdt, BalanceBucket.RESERVED, new BigDecimal("100")));

		assertEquals(true, ledgerService.post("reserve:retry-1", LedgerType.RESERVE, reserve));
		assertEquals(false, ledgerService.post("reserve:retry-1", LedgerType.RESERVE, reserve));
		var balance = balanceRepository.findByAccountIdOrderByAssetSymbolAsc(account.getId()).get(2);
		assertEquals(0, new BigDecimal("9900").compareTo(balance.getAvailable()));
		assertEquals(0, new BigDecimal("100").compareTo(balance.getReserved()));
	}

	@Test
	void ledgerReplayGivesTheStoredBalancesAndEveryAssetSumsToZero() {
		var owner = newUser("wallet-replay@example.com");
		var account = ledgerService.findOrOpen(owner.getId());
		var usdt = assetRepository.findBySymbol("USDT").orElseThrow();
		ledgerService.post("reserve:replay-1", LedgerType.RESERVE,
				List.of(new Leg(account, usdt, BalanceBucket.AVAILABLE, new BigDecimal("-250.5")),
						new Leg(account, usdt, BalanceBucket.RESERVED, new BigDecimal("250.5"))));

		var lines = ledgerEntryRepository.findAll();
		for (var bucket : BalanceBucket.values()) {
			var replayed = lines.stream()
					.filter(line -> line.getAccount().getId().equals(account.getId())
							&& line.getAsset().getId().equals(usdt.getId()) && line.getBucket() == bucket)
					.map(LedgerEntryEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
			var stored = balanceRepository.findByAccountIdOrderByAssetSymbolAsc(account.getId()).get(2);
			assertEquals(0, replayed
					.compareTo(bucket == BalanceBucket.AVAILABLE ? stored.getAvailable() : stored.getReserved()));
		}
		var total = lines.stream().filter(line -> line.getAsset().getId().equals(usdt.getId()))
				.map(LedgerEntryEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertEquals(0, total.signum());
	}

	// runs the task on several threads released at the same moment
	private <T> List<T> runTogether(int threads, Callable<T> task) throws Exception {
		var pool = Executors.newFixedThreadPool(threads);
		try {
			var ready = new CountDownLatch(threads);
			var go = new CountDownLatch(1);
			var futures = new java.util.ArrayList<java.util.concurrent.Future<T>>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return task.call();
				}));
			}
			ready.await();
			go.countDown();
			var out = new java.util.ArrayList<T>();
			for (var future : futures) {
				out.add(future.get());
			}
			return out;
		} finally {
			pool.shutdownNow();
		}
	}

	// Market orders: BTC-USDT is priced at 100.00 here so the sums are easy to check.
	private static String order(String symbol, String side, String quantity) {
		return "{\"symbol\":\"" + symbol + "\",\"side\":\"" + side + "\",\"type\":\"MARKET\",\"quantity\":\"" + quantity
				+ "\"}";
	}

	private String balance(UserEntity owner, int index, String field) throws Exception {
		var body = mockMvc.perform(get("/api/v1/accounts/me/balances").with(user(owner))).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(body, "$[" + index + "]." + field);
	}

	@Test
	void marketOrdersMoveMoneyAndRetriesDoNotRepeatThem() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("trader-1@example.com");

		var buy = mockMvc
				.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "k-1")
						.contentType(MediaType.APPLICATION_JSON).content(order("btc-usdt", "BUY", "2")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FILLED"))
				.andExpect(jsonPath("$.symbol").value("BTC-USDT")).andExpect(jsonPath("$.quantity").value("2.00000000"))
				.andReturn();
		String orderId = JsonPath.read(buy.getResponse().getContentAsString(), "$.id");
		assertEquals("9800.000000000000000000", balance(owner, 2, "available"));
		assertEquals("2.000000000000000000", balance(owner, 0, "available"));

		// same key and same request (2.0 is the same number as 2): the first order comes back, nothing moves again
		mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "k-1")
				.contentType(MediaType.APPLICATION_JSON).content(order("BTC-USDT", "BUY", "2.0")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(orderId));
		assertEquals("9800.000000000000000000", balance(owner, 2, "available"));
		// same key, different request
		mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "k-1")
				.contentType(MediaType.APPLICATION_JSON).content(order("BTC-USDT", "BUY", "3")))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "k-2")
				.contentType(MediaType.APPLICATION_JSON).content(order("BTC-USDT", "SELL", "0.5")))
				.andExpect(status().isCreated());
		assertEquals("9850.000000000000000000", balance(owner, 2, "available"));
		assertEquals("1.500000000000000000", balance(owner, 0, "available"));

		mockMvc.perform(get("/api/v1/orders").with(user(owner))).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.page.totalElements").value(2));
		mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(user(owner))).andExpect(status().isOk())
				.andExpect(jsonPath("$.side").value("BUY"));
		mockMvc.perform(get("/api/v1/trades").with(user(owner))).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].price").value("100.00"))
				.andExpect(jsonPath("$.content[0].quantity").value("0.50000000"))
				.andExpect(jsonPath("$.content[0].quoteVersion").isNumber())
				.andExpect(jsonPath("$.content[1].orderId").value(orderId));

		// another user cannot see it, and the answer does not say it exists
		var other = newUser("trader-2@example.com");
		mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(user(other))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/orders").with(user(other))).andExpect(jsonPath("$.content.length()").value(0));
		mockMvc.perform(get("/api/v1/trades").with(user(other))).andExpect(jsonPath("$.content.length()").value(0));
	}

	@Test
	void badOrdersSaveNothing() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("trader-3@example.com");

		// 1,000 BTC costs 100,000 USDT; the user has 10,000
		mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "big")
				.contentType(MediaType.APPLICATION_JSON).content(order("BTC-USDT", "BUY", "1000")))
				.andExpect(status().isUnprocessableEntity());
		// no coin to sell
		mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "short")
				.contentType(MediaType.APPLICATION_JSON).content(order("BTC-USDT", "SELL", "1")))
				.andExpect(status().isUnprocessableEntity());
		assertEquals("10000.000000000000000000", balance(owner, 2, "available"));
		mockMvc.perform(get("/api/v1/orders").with(user(owner))).andExpect(jsonPath("$.content.length()").value(0));
		// the failed key was rolled back too, so it can be used again
		mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "big")
				.contentType(MediaType.APPLICATION_JSON).content(order("BTC-USDT", "BUY", "1")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/orders").with(user(owner)).contentType(MediaType.APPLICATION_JSON)
				.content(order("BTC-USDT", "BUY", "1"))).andExpect(status().isBadRequest());
		for (var body : new String[]{order("BTC-USDT", "BUY", "0"), order("BTC-USDT", "BUY", "-1"),
				order("BTC-USDT", "BUY", "0.123456789"), order("BTC-USDT", "HOLD", "1"),
				order("BTC-USDT", "BUY", "abc"),
				"{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":\"1\"}",
				"{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"type\":\"MARKET\"}"}) {
			mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "bad")
					.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
		}
		mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "x")
				.contentType(MediaType.APPLICATION_JSON).content(order("DOGE-USDT", "BUY", "1")))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "x").contentType(MediaType.APPLICATION_JSON)
				.content(order("BTC-USDT", "BUY", "1"))).andExpect(status().isUnauthorized());
	}

	@Test
	void closedMarketAndMissingQuoteRejectOrders() throws Exception {
		var owner = newUser("trader-4@example.com");
		// ETH-USDT is never given a quote by the other tests
		mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "e-1")
				.contentType(MediaType.APPLICATION_JSON).content(order("ETH-USDT", "BUY", "1")))
				.andExpect(status().isNotFound());

		var eth = marketRepository.findByPair("ETH-USDT").orElseThrow();
		eth.setStatus(Common.SUSPENDED);
		marketRepository.saveAndFlush(eth);
		try {
			mockMvc.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", "e-2")
					.contentType(MediaType.APPLICATION_JSON).content(order("ETH-USDT", "BUY", "1")))
					.andExpect(status().isConflict());
		} finally {
			eth.setStatus(Common.ACTIVE);
			marketRepository.saveAndFlush(eth);
		}
	}

	@Test
	void concurrentRetriesOfOneKeyMakeOneOrder() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("trader-5@example.com");
		var request = new SubmitOrderRequest("BTC-USDT", OrderSide.BUY, OrderType.MARKET, new BigDecimal("1"), null);

		var ids = runTogether(6, () -> orderService.submit(owner.getId(), "same-key", request).id());

		assertEquals(1, ids.stream().distinct().count());
		assertEquals("9900.000000000000000000", balance(owner, 2, "available"));
	}

	@Test
	void concurrentOrdersCannotSpendMoreThanTheUserHas() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("trader-6@example.com");
		var request = new SubmitOrderRequest("BTC-USDT", OrderSide.BUY, OrderType.MARKET, new BigDecimal("60"), null);
		var counter = new java.util.concurrent.atomic.AtomicInteger();

		// two different orders of 6,000 USDT from 10,000: exactly one fits
		var filled = runTogether(2, () -> {
			try {
				orderService.submit(owner.getId(), "spend-" + counter.incrementAndGet(), request);
				return true;
			} catch (InsufficientFundsException e) {
				return false;
			}
		});

		assertEquals(1, filled.stream().filter(done -> done).count());
		assertEquals("4000.000000000000000000", balance(owner, 2, "available"));
		assertEquals(1, orderService.list(owner.getId(), null, 0, 20).getTotalElements());
	}

	// Limit orders and cancel
	private static String limitOrder(String side, String quantity, String limitPrice) {
		return "{\"symbol\":\"BTC-USDT\",\"side\":\"" + side + "\",\"type\":\"LIMIT\",\"quantity\":\"" + quantity
				+ "\",\"limitPrice\":\"" + limitPrice + "\"}";
	}

	private String placeOrder(UserEntity owner, String key, String body, int expectedStatus) throws Exception {
		var result = mockMvc
				.perform(post("/api/v1/orders").with(user(owner)).header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().is(expectedStatus)).andReturn();
		return expectedStatus == 201 ? JsonPath.read(result.getResponse().getContentAsString(), "$.id") : null;
	}

	@Test
	void limitBuyReservesFundsAndCancelReleasesThemOnce() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("limit-1@example.com");

		// price is 100, limit is 90: not reached, so the order waits and 90 USDT is held back
		var orderId = placeOrder(owner, "l-1", limitOrder("BUY", "1", "90"), 201);
		mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(user(owner)))
				.andExpect(jsonPath("$.status").value("OPEN")).andExpect(jsonPath("$.type").value("LIMIT"))
				.andExpect(jsonPath("$.limitPrice").value("90.00"));
		assertEquals("9910.000000000000000000", balance(owner, 2, "available"));
		assertEquals("90.000000000000000000", balance(owner, 2, "reserved"));
		// a retry of the same request does not reserve twice
		placeOrder(owner, "l-1", limitOrder("BUY", "1", "90.0"), 201);
		assertEquals("90.000000000000000000", balance(owner, 2, "reserved"));
		// another user cannot cancel it, and is told nothing exists
		mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(user(newUser("limit-other@example.com"))))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(user(owner))).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertEquals("10000.000000000000000000", balance(owner, 2, "available"));
		assertEquals("0.000000000000000000", balance(owner, 2, "reserved"));
		// cancelling again gives the same order and releases nothing more
		mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(user(owner))).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId)).andExpect(jsonPath("$.status").value("CANCELLED"));
		assertEquals("10000.000000000000000000", balance(owner, 2, "available"));
		mockMvc.perform(get("/api/v1/trades").with(user(owner))).andExpect(jsonPath("$.content.length()").value(0));
	}

	@Test
	void limitOrderThatIsAlreadyReachedFillsAtTheQuoteAndRefundsTheRest() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("limit-2@example.com");

		var orderId = placeOrder(owner, "l-2", limitOrder("BUY", "2", "110"), 201);
		mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(user(owner)))
				.andExpect(jsonPath("$.status").value("FILLED"));
		// paid 2 x 100, not 2 x 110; nothing stays reserved
		assertEquals("9800.000000000000000000", balance(owner, 2, "available"));
		assertEquals("0.000000000000000000", balance(owner, 2, "reserved"));
		assertEquals("2.000000000000000000", balance(owner, 0, "available"));
		mockMvc.perform(get("/api/v1/trades").with(user(owner)))
				.andExpect(jsonPath("$.content[0].price").value("100.00"));
		// a filled order cannot be cancelled
		mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(user(owner))).andExpect(status().isConflict());
	}

	@Test
	void theMatchingJobFillsOrdersWhenThePriceArrivesAndSellsWorkToo() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("limit-3@example.com");
		placeOrder(owner, "b-1", order("BTC-USDT", "BUY", "2"), 201);
		var buyId = placeOrder(owner, "l-3", limitOrder("BUY", "1", "90"), 201);
		var sellId = placeOrder(owner, "l-4", limitOrder("SELL", "1", "120"), 201);
		// a sell holds the coin: 2 owned, 1 held back
		assertEquals("1.000000000000000000", balance(owner, 0, "available"));
		assertEquals("1.000000000000000000", balance(owner, 0, "reserved"));

		// nothing has been reached yet
		orderService.matchOpenOrders();
		assertEquals("OPEN", orderService.get(owner.getId(), java.util.UUID.fromString(buyId)).status().name());

		marketService.publishQuote("BTC-USDT", new BigDecimal("85"));
		orderService.matchOpenOrders();
		assertEquals("FILLED", orderService.get(owner.getId(), java.util.UUID.fromString(buyId)).status().name());
		assertEquals("OPEN", orderService.get(owner.getId(), java.util.UUID.fromString(sellId)).status().name());
		// filled at 85, not 90: 5 of the reserve came back
		assertEquals("9715.000000000000000000", balance(owner, 2, "available"));
		assertEquals("0.000000000000000000", balance(owner, 2, "reserved"));
		assertEquals("2.000000000000000000", balance(owner, 0, "available"));

		marketService.publishQuote("BTC-USDT", new BigDecimal("125"));
		orderService.matchOpenOrders();
		assertEquals("FILLED", orderService.get(owner.getId(), java.util.UUID.fromString(sellId)).status().name());
		assertEquals("9840.000000000000000000", balance(owner, 2, "available"));
		// bought 2, then 1 more, then sold 1
		assertEquals("2.000000000000000000", balance(owner, 0, "available"));
		assertEquals("0.000000000000000000", balance(owner, 0, "reserved"));
		// running the job again changes nothing
		orderService.matchOpenOrders();
		assertEquals("9840.000000000000000000", balance(owner, 2, "available"));
	}

	@Test
	void limitOrdersAreCheckedAndNeverLeaveReservesBehindWhenTheyFail() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("limit-4@example.com");

		// 200 x 90 = 18,000 USDT is more than the 10,000 held
		placeOrder(owner, "x-1", limitOrder("BUY", "200", "90"), 422);
		placeOrder(owner, "x-2", limitOrder("SELL", "1", "120"), 422);
		assertEquals("10000.000000000000000000", balance(owner, 2, "available"));
		assertEquals("0.000000000000000000", balance(owner, 2, "reserved"));
		mockMvc.perform(get("/api/v1/orders").with(user(owner))).andExpect(jsonPath("$.content.length()").value(0));

		placeOrder(owner, "x-3", order("BTC-USDT", "BUY", "1").replace("MARKET", "LIMIT"), 400);
		placeOrder(owner, "x-4", limitOrder("BUY", "1", "90.123"), 400);
		placeOrder(owner, "x-5", limitOrder("BUY", "1", "0"), 400);
		placeOrder(owner, "x-6", order("BTC-USDT", "BUY", "1").replace("}", ",\"limitPrice\":\"90\"}"), 400);
		mockMvc.perform(post("/api/v1/orders/{id}/cancel", java.util.UUID.randomUUID()).with(user(owner)))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/orders/{id}/cancel", java.util.UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void cancelAndFillAtTheSameTimeGiveExactlyOneResult() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("limit-5@example.com");
		var orderId = java.util.UUID.fromString(placeOrder(owner, "r-1", limitOrder("BUY", "1", "90"), 201));
		marketService.publishQuote("BTC-USDT", new BigDecimal("80"));

		var turn = new java.util.concurrent.atomic.AtomicInteger();
		var results = runTogether(2, () -> {
			try {
				// one thread runs the job, the other cancels
				if (turn.getAndIncrement() == 0) {
					orderService.matchOpenOrders();
					return "match";
				}
				return orderService.cancel(owner.getId(), orderId).status().name();
			} catch (RuntimeException e) {
				return e.getClass().getSimpleName();
			}
		});

		var finalStatus = orderService.get(owner.getId(), orderId).status().name();
		// whichever won, the reserve is gone, and the money is either all back or spent at 80
		assertEquals("0.000000000000000000", balance(owner, 2, "reserved"));
		if (finalStatus.equals("CANCELLED")) {
			assertEquals("10000.000000000000000000", balance(owner, 2, "available"));
			assertEquals("0.000000000000000000", balance(owner, 0, "available"));
		} else {
			assertEquals("FILLED", finalStatus);
			assertEquals("9920.000000000000000000", balance(owner, 2, "available"));
			assertEquals("1.000000000000000000", balance(owner, 0, "available"));
		}
		assertEquals(2, results.size());
	}

	// Portfolio, admin and API docs
	@Autowired
	private AuditLogRepository auditLogRepository;

	private UserEntity newAdmin(String email) {
		var admin = newUser(email);
		admin.setRole(RoleType.ADMIN);
		return userRepository.saveAndFlush(admin);
	}

	private void changeStatus(UserEntity admin, String path, String status, int expectedStatus) throws Exception {
		mockMvc.perform(patch(path).with(user(admin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + status + "\"}")).andExpect(status().is(expectedStatus));
	}

	@Test
	void portfolioValuesEachAssetInUsdtAndCountsReservedAmounts() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("portfolio-1@example.com");
		placeOrder(owner, "p-1", order("BTC-USDT", "BUY", "2"), 201);
		// one BTC is held back by an open sell order; it still counts toward the value
		placeOrder(owner, "p-2", limitOrder("SELL", "1", "150"), 201);

		var body = mockMvc.perform(get("/api/v1/portfolio").with(user(owner))).andExpect(status().isOk())
				.andExpect(jsonPath("$.quoteTime").exists()).andReturn().getResponse().getContentAsString();
		assertEquals(List.of("1.000000000000000000"), JsonPath.read(body, "$.assets[?(@.asset=='BTC')].available"));
		assertEquals(List.of("1.000000000000000000"), JsonPath.read(body, "$.assets[?(@.asset=='BTC')].reserved"));
		assertEquals(List.of("200.00"), JsonPath.read(body, "$.assets[?(@.asset=='BTC')].valueUsdt"));
		assertEquals(List.of("9800.00"), JsonPath.read(body, "$.assets[?(@.asset=='USDT')].valueUsdt"));

		// another user sees only their own money
		var otherBody = mockMvc.perform(get("/api/v1/portfolio").with(user(newUser("portfolio-2@example.com"))))
				.andReturn().getResponse().getContentAsString();
		assertEquals(List.of("0.00"), JsonPath.read(otherBody, "$.assets[?(@.asset=='BTC')].valueUsdt"));
		assertEquals(List.of("10000.00"), JsonPath.read(otherBody, "$.assets[?(@.asset=='USDT')].valueUsdt"));
		mockMvc.perform(get("/api/v1/portfolio")).andExpect(status().isUnauthorized());
	}

	@Test
	void ordersCanBeFilteredByStatus() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("filter-1@example.com");
		placeOrder(owner, "f-1", order("BTC-USDT", "BUY", "1"), 201);
		var openId = placeOrder(owner, "f-2", limitOrder("BUY", "1", "90"), 201);

		mockMvc.perform(get("/api/v1/orders").with(user(owner))).andExpect(jsonPath("$.content.length()").value(2));
		mockMvc.perform(get("/api/v1/orders").param("status", "OPEN").with(user(owner))).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(openId));
		mockMvc.perform(get("/api/v1/orders").param("status", "FILLED").with(user(owner)))
				.andExpect(jsonPath("$.content.length()").value(1));
		mockMvc.perform(get("/api/v1/orders").param("status", "NOPE").with(user(owner)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void suspendedMarketTakesNoOrdersAndNoFillsButCancelStillWorks() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var admin = newAdmin("market-switch-admin@example.com");
		var owner = newUser("market-switch-1@example.com");
		var openId = placeOrder(owner, "m-1", limitOrder("BUY", "1", "90"), 201);
		var changesBefore = auditLogRepository.findByActionOrderByCreatedAtAsc(AuditAction.MARKET_STATUS_CHANGED)
				.size();
		var quotesBefore = auditLogRepository.findByActionOrderByCreatedAtAsc(AuditAction.QUOTE_PUBLISHED).size();

		try {
			mockMvc.perform(patch("/api/v1/admin/markets/btc-usdt/status").with(user(admin))
					.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.pair").value("BTC-USDT"))
					.andExpect(jsonPath("$.status").value("SUSPENDED"));
			// no new orders
			placeOrder(owner, "m-2", order("BTC-USDT", "BUY", "1"), 409);
			placeOrder(owner, "m-3", limitOrder("BUY", "1", "90"), 409);
			// no fills, even when the price reaches the limit
			mockMvc.perform(post("/api/v1/admin/markets/BTC-USDT/quotes").with(user(admin))
					.contentType(MediaType.APPLICATION_JSON).content("{\"price\":\"80\"}"))
					.andExpect(status().isCreated());
			orderService.matchOpenOrders();
			mockMvc.perform(get("/api/v1/orders/{id}", openId).with(user(owner)))
					.andExpect(jsonPath("$.status").value("OPEN"));
			// cancel still works
			mockMvc.perform(post("/api/v1/orders/{id}/cancel", openId).with(user(owner))).andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("CANCELLED"));
			assertEquals("10000.000000000000000000", balance(owner, 2, "available"));
		} finally {
			changeStatus(admin, "/api/v1/admin/markets/BTC-USDT/status", "ACTIVE", 200);
			marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		}
		placeOrder(owner, "m-4", order("BTC-USDT", "BUY", "1"), 201);

		// two switches and one published price, each with an audit row
		assertEquals(changesBefore + 2,
				auditLogRepository.findByActionOrderByCreatedAtAsc(AuditAction.MARKET_STATUS_CHANGED).size());
		assertEquals(quotesBefore + 1,
				auditLogRepository.findByActionOrderByCreatedAtAsc(AuditAction.QUOTE_PUBLISHED).size());
		var lastChange = auditLogRepository.findByActionOrderByCreatedAtAsc(AuditAction.MARKET_STATUS_CHANGED)
				.getLast();
		assertEquals(admin.getId(), lastChange.getAdminId());
		assertEquals("BTC-USDT", lastChange.getTarget());
		assertEquals("ACTIVE", lastChange.getDetail());
	}

	@Test
	void marketStatusRoutesRejectNormalUsersAndBadInput() throws Exception {
		var admin = newAdmin("market-switch-admin-2@example.com");
		var normalUser = newUser("market-switch-2@example.com");
		changeStatus(normalUser, "/api/v1/admin/markets/BTC-USDT/status", "SUSPENDED", 403);
		mockMvc.perform(patch("/api/v1/admin/markets/BTC-USDT/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isUnauthorized());
		changeStatus(admin, "/api/v1/admin/markets/BTC-USDT/status", "DELETED", 400);
		changeStatus(admin, "/api/v1/admin/markets/BTC-USDT/status", "PENDING", 400);
		changeStatus(admin, "/api/v1/admin/markets/DOGE-USDT/status", "SUSPENDED", 404);
		mockMvc.perform(patch("/api/v1/admin/markets/BTC-USDT/status").with(user(admin))
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
		assertEquals(Common.ACTIVE, marketRepository.findByPair("BTC-USDT").orElseThrow().getStatus());
	}

	@Test
	void suspendingAUserCancelsTheirOrdersReleasesReservesAndBlocksTheirToken() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var admin = newAdmin("user-switch-admin@example.com");
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"suspend-me@example.com","password":"correct-horse","confirmPassword":"correct-horse"}
				""")).andExpect(status().isCreated());
		var tokens = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"suspend-me@example.com","password":"correct-horse"}
				""")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(tokens, "$.accessToken");
		var target = userRepository.findByEmail("suspend-me@example.com").orElseThrow();

		placeOrder(target, "s-1", order("BTC-USDT", "BUY", "1"), 201);
		var buyId = placeOrder(target, "s-2", limitOrder("BUY", "1", "90"), 201);
		var sellId = placeOrder(target, "s-3", limitOrder("SELL", "1", "150"), 201);
		assertEquals("90.000000000000000000", balance(target, 2, "reserved"));
		assertEquals("1.000000000000000000", balance(target, 0, "reserved"));
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk());
		var changesBefore = auditLogRepository.findByActionOrderByCreatedAtAsc(AuditAction.USER_STATUS_CHANGED).size();

		mockMvc.perform(patch("/api/v1/admin/users/{id}/status", target.getId()).with(user(admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));

		for (var orderId : List.of(buyId, sellId)) {
			mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(user(target)))
					.andExpect(jsonPath("$.status").value("CANCELLED"));
		}
		assertEquals("9900.000000000000000000", balance(target, 2, "available"));
		assertEquals("0.000000000000000000", balance(target, 2, "reserved"));
		assertEquals("1.000000000000000000", balance(target, 0, "available"));
		assertEquals("0.000000000000000000", balance(target, 0, "reserved"));
		assertEquals(Common.SUSPENDED, userRepository.findById(target.getId()).orElseThrow().getStatus());
		// the token they already hold stops working at once
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isUnauthorized());

		// suspending twice changes nothing more; reactivating gives the token back
		changeStatus(admin, "/api/v1/admin/users/" + target.getId() + "/status", "SUSPENDED", 200);
		assertEquals("9900.000000000000000000", balance(target, 2, "available"));
		changeStatus(admin, "/api/v1/admin/users/" + target.getId() + "/status", "ACTIVE", 200);
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk());
		assertEquals(changesBefore + 3,
				auditLogRepository.findByActionOrderByCreatedAtAsc(AuditAction.USER_STATUS_CHANGED).size());
	}

	@Test
	void userStatusRouteRejectsNormalUsersSelfChangeAndBadInput() throws Exception {
		var admin = newAdmin("user-switch-admin-2@example.com");
		var normalUser = newUser("user-switch-2@example.com");
		var path = "/api/v1/admin/users/" + normalUser.getId() + "/status";
		changeStatus(normalUser, path, "SUSPENDED", 403);
		changeStatus(normalUser, "/api/v1/admin/users/" + admin.getId() + "/status", "SUSPENDED", 403);
		changeStatus(admin, "/api/v1/admin/users/" + admin.getId() + "/status", "SUSPENDED", 409);
		changeStatus(admin, path, "DELETED", 400);
		changeStatus(admin, "/api/v1/admin/users/" + java.util.UUID.randomUUID() + "/status", "SUSPENDED", 404);
		assertEquals(Common.ACTIVE, userRepository.findById(normalUser.getId()).orElseThrow().getStatus());
		assertEquals(Common.ACTIVE, userRepository.findById(admin.getId()).orElseThrow().getStatus());
	}

	@Test
	void suspendingAUserWhileTheMatcherRunsNeverLeavesAReserveBehind() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("120"));
		var owner = newUser("suspend-race@example.com");
		var orderId = placeOrder(owner, "r-1", limitOrder("BUY", "1", "100"), 201);
		// the price drops to the limit, so the matcher wants to fill the same order
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));

		var next = new java.util.concurrent.atomic.AtomicInteger();
		runTogether(2, () -> {
			if (next.getAndIncrement() == 0) {
				return orderService.cancelOpenOrders(owner.getId());
			}
			orderService.matchOpenOrders();
			return 0;
		});

		var finalStatus = JsonPath.<String>read(mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(user(owner)))
				.andReturn().getResponse().getContentAsString(), "$.status");
		assertEquals("0.000000000000000000", balance(owner, 2, "reserved"));
		if (finalStatus.equals("FILLED")) {
			assertEquals("9900.000000000000000000", balance(owner, 2, "available"));
			assertEquals("1.000000000000000000", balance(owner, 0, "available"));
		} else {
			assertEquals("CANCELLED", finalStatus);
			assertEquals("10000.000000000000000000", balance(owner, 2, "available"));
		}
	}

	@Test
	void apiDocsArePublicAndListThePortfolioAndAdminRoutes() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/portfolio']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/orders/{id}/cancel']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/users/{id}/status']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/markets/{pair}/status']").exists());
	}

	@Test
	void aSuspendedUserCannotPlaceOrdersAndTheirOpenOrderIsNotFilled() throws Exception {
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
		var owner = newUser("suspended-race@example.com");
		var orderId = placeOrder(owner, "q-1", limitOrder("BUY", "1", "90"), 201);

		// the suspension lands after the request passed the login check
		owner.setStatus(Common.SUSPENDED);
		userRepository.saveAndFlush(owner);
		placeOrder(owner, "q-2", limitOrder("BUY", "1", "90"), 409);
		marketService.publishQuote("BTC-USDT", new BigDecimal("90"));
		orderService.matchOpenOrders();
		mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(user(owner)))
				.andExpect(jsonPath("$.status").value("OPEN"));

		owner.setStatus(Common.ACTIVE);
		userRepository.saveAndFlush(owner);
		orderService.matchOpenOrders();
		mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(user(owner)))
				.andExpect(jsonPath("$.status").value("FILLED"));
		marketService.publishQuote("BTC-USDT", new BigDecimal("100"));
	}

	@Test
	void apiDocsAreNotBlockedByAStaleToken() throws Exception {
		mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer not-a-real-token"))
				.andExpect(status().isOk());
	}
}
