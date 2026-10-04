package trading.demo.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import trading.demo.model.enums.Common;
import trading.demo.model.enums.RoleType;
import trading.demo.model.enums.UserType;

class UserEntityTest {
	@Test
	void normalizesEmailAndDefaultsToOrdinaryActiveTrader() {
		UserEntity user = new UserEntity();
		user.setEmail("  Alice@Example.COM  ");
		assertEquals("alice@example.com", user.getEmail());
		assertEquals(RoleType.USER, user.getRole());
		assertEquals(Common.ACTIVE, user.getStatus());
		assertEquals(UserType.TRADER, user.getUserType());
		assertTrue(user.getAddresses().isEmpty());
	}

	@Test
	void hidesHashAndRejectsJsonPrivilegeAssignments() {
		JsonMapper mapper = JsonMapper.builder().build();
		UserEntity user = mapper.readValue("""
				{"email":"Alice@Example.COM", "role":"ADMIN", "status":"SUSPENDED",
				 "userType":"DEALER", "password":"untrusted"}
				""", UserEntity.class);
		assertEquals(RoleType.USER, user.getRole());
		assertEquals(Common.ACTIVE, user.getStatus());
		assertEquals(UserType.TRADER, user.getUserType());
		assertNull(user.getPassword());
		user.setPassword("test-only-secret-hash");
		String json = mapper.writeValueAsString(user);
		assertFalse(json.contains("password"));
		assertFalse(json.contains("test-only-secret-hash"));
	}

	@Test
	void stampsCreationAndUpdateInUtc() {
		UserEntity user = new UserEntity();
		user.onCreate();
		var created = user.getCreatedAt();
		assertNotNull(created);
		assertEquals(created, user.getUpdatedAt());
		user.onUpdate();
		assertEquals(created, user.getCreatedAt());
		assertFalse(user.getUpdatedAt().isBefore(created));
	}
}
