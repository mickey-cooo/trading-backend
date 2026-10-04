package trading.demo.controller;

import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.userDto.LoginDto;
import trading.demo.model.dto.userDto.RegisterDTO;
import trading.demo.model.dto.userDto.UserResponseDTO;
import trading.demo.security.JwtTokenProvider;
import trading.demo.service.UserService;

@RestController
@RequestMapping("/auth")
public class AuthController {
	private final UserService userService;
	private final AuthenticationManager authenticationManager;
	private final JwtTokenProvider jwtTokenProvider;

	public AuthController(UserService userService, AuthenticationManager authenticationManager,
			JwtTokenProvider jwtTokenProvider) {
		this.userService = userService;
		this.authenticationManager = authenticationManager;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@PostMapping("/signup")
	public ResponseEntity<UserResponseDTO> signUp(@Valid @RequestBody RegisterDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userService.signUp(request));
	}

	@PostMapping("/login")
	public Map<String, Object> login(@Valid @RequestBody LoginDto request) {
		var authentication = authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
		var user = (UserDetails) authentication.getPrincipal();
		return Map.of("token", jwtTokenProvider.generateToken(user), "expiresIn", jwtTokenProvider.getExpirationTime());
	}
}
