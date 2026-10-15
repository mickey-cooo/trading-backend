package trading.demo.model.dto.userDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginDto {
	@NotBlank
	@Email
	@Size(max = 254)
	private String email;

	// same ceiling as RegisterDTO: bounds what reaches Argon2
	@NotBlank
	@Size(max = 72)
	private String password;
}
