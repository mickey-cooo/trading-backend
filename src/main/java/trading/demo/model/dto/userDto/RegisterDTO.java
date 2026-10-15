package trading.demo.model.dto.userDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDTO {
	// ASCII only: non-ASCII lookalikes (dotless i, long s) register as distinct
	// accounts
	@NotBlank
	@Email
	@Size(max = 254)
	@Pattern(regexp = "\\p{ASCII}+")
	private String email;

	@NotBlank
	@Size(min = 8, max = 72)
	private String password;

	@NotBlank
	@Size(max = 72)
	private String confirmPassword;
}
