package trading.demo.service;

import trading.demo.model.dto.userDto.AuthTokensResponse;
import trading.demo.model.dto.userDto.LoginDto;

public interface AuthService {
	AuthTokensResponse login(LoginDto request);

	AuthTokensResponse refresh(String refreshToken);

	void logout(String refreshToken);
}
