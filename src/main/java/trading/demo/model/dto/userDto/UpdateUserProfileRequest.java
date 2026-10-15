package trading.demo.model.dto.userDto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserProfileRequest {
	// PATCH: null means "leave unchanged", a sent value must not be blank
	@Size(max = 100)
	@Pattern(regexp = "(?s).*\\S.*")
	private String firstName;

	@Size(max = 100)
	@Pattern(regexp = "(?s).*\\S.*")
	private String lastName;
}
