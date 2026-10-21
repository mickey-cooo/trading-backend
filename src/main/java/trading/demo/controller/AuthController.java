package trading.demo.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.userDto.AuthTokensResponse;
import trading.demo.model.dto.userDto.LoginDto;
import trading.demo.model.dto.userDto.RefreshTokenRequest;
import trading.demo.model.dto.userDto.RegisterDTO;
import trading.demo.model.dto.userDto.UserResponseDTO;
import trading.demo.service.AuthService;
import trading.demo.service.UserService;

@RestController
@RequestMapping("/auth")
public class AuthController {
	private final UserService userService;
	private final AuthService authService;

	public AuthController(UserService userService, AuthService authService) {
		this.userService = userService;
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
	}

	@PostMapping("/login")
	public AuthTokensResponse login(@Valid @RequestBody LoginDto request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	public AuthTokensResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.refresh(request.refreshToken());
	}

	// public on purpose: the refresh token is the credential, and the access token may already be expired
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}
}
