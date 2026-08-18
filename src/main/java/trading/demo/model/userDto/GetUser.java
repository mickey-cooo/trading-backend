package trading.demo.model.userDto;

import jakarta.validation.constraints.NotBlank;

public class GetUser {
    @NotBlank
    private String id;
}
