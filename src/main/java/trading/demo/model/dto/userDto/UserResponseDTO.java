package trading.demo.model.dto.userDto;

import java.time.Instant;

import lombok.Data;
import trading.demo.model.enums.Common;
import trading.demo.model.enums.RoleType;
import trading.demo.model.enums.UserType;

@Data
public class UserResponseDTO {
	private String id;
	private String firstName;
	private String lastName;
	private Common status;
	private RoleType role;
	private UserType userType;
	private Instant createdAt;
	private Instant updatedAt;
}
