package trading.demo.model.dto.userDto;

import jakarta.validation.constraints.NotBlank;

public class CreateUserProfileRequest {
	@NotBlank
	private String firstName;

	@NotBlank
	private String lastName;

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}
}