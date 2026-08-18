package trading.demo.model.userDto;

import jakarta.validation.constraints.NotBlank;

public class CreateUserProfileRequest {
	@NotBlank
	private String firstName;

	@NotBlank
	private String lastName;
}
