package trading.demo.model.dto.userDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDTO {
	@NotBlank
	@Email
	@Size(max = 254)
	private String email;

	@NotBlank
	@Size(min = 8, max = 72)
	private String password;

	@NotBlank
	private String confirmPassword;
}
