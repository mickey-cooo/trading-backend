package trading.demo.model.dto.userDto;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserProfileRequest {
    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    private String email;

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
