package trading.demo.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import trading.demo.model.dto.userDto.CreateUserProfileRequest;
import trading.demo.model.dto.userDto.UpdateUserProfileRequest;
import trading.demo.model.entity.UserEntity;
import trading.demo.model.enums.Common;
import trading.demo.model.enums.RoleType;
import trading.demo.model.enums.UserType;

class UserMapperTest {
	@Test
	void mapsProfileFields() {
		var id = UUID.randomUUID();
		var createdAt = Instant.parse("2026-01-01T00:00:00Z");
		var updatedAt = Instant.parse("2026-01-02T00:00:00Z");
		var user = new UserEntity();
		user.setId(id);
		user.setFirstName("Alice");
		user.setLastName("Example");
		user.setEmail("Alice@Example.COM");
		user.setPassword("not-for-profile");
		user.setStatus(Common.ACTIVE);
		user.setRole(RoleType.USER);
		user.setUserType(UserType.TRADER);
		user.setCreatedAt(createdAt);
		user.setUpdatedAt(updatedAt);

		var profile = new UserMapper().toDTO(user);

		assertEquals(id.toString(), profile.getId());
		assertEquals("Alice", profile.getFirstName());
		assertEquals("Example", profile.getLastName());
		assertEquals(Common.ACTIVE, profile.getStatus());
		assertEquals(RoleType.USER, profile.getRole());
		assertEquals(UserType.TRADER, profile.getUserType());
		assertEquals(createdAt, profile.getCreatedAt());
		assertEquals(updatedAt, profile.getUpdatedAt());
	}

	@Test
	void mapsCreateAndUpdateProfileFields() {
		var createRequest = new CreateUserProfileRequest();
		createRequest.setFirstName("Alice");
		createRequest.setLastName("Example");

		var mapper = new UserMapper();
		var user = mapper.toEntity(createRequest, new UserEntity());

		assertEquals("Alice", user.getFirstName());
		assertEquals("Example", user.getLastName());
		assertNull(user.getEmail());

		user.setEmail("Alice@Example.COM");

		var updateRequest = new UpdateUserProfileRequest();
		updateRequest.setFirstName("Alicia");
		updateRequest.setLastName("Updated");
		mapper.updateEntity(updateRequest, user);

		assertEquals("Alicia", user.getFirstName());
		assertEquals("Updated", user.getLastName());
		assertEquals("alice@example.com", user.getEmail());
	}
}
