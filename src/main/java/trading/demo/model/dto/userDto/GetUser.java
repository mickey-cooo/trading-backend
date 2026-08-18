package trading.demo.model.dto.userDto;

import jakarta.validation.constraints.NotBlank;

public class GetUser {
    @NotBlank
    private String id;
}
