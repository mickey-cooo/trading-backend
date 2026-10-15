package trading.demo.model.dto.userDto;

public record AuthTokensResponse(String accessToken, String refreshToken, long expiresIn) {
}
