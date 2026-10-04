package trading.demo.controller;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.userDto.CreateUserProfileRequest;
import trading.demo.model.dto.userDto.UpdateUserProfileRequest;
import trading.demo.model.dto.userDto.UserResponseDTO;
import trading.demo.service.UserService;

@RestController
@Validated
@RequestMapping("/api/v1/users")
public class UserController {
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping("/{id}/profile")
	public ResponseEntity<UserResponseDTO> createUserProfile(@PathVariable UUID id,
			@Valid @RequestBody CreateUserProfileRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUserProfile(id, request));
	}

	@GetMapping("/{id}")
	public ResponseEntity<UserResponseDTO> getUserById(@PathVariable UUID id) {
		return ResponseEntity.ok(userService.getUserById(id));
	}

	@GetMapping
	public ResponseEntity<List<UserResponseDTO>> getListUser(@RequestParam @Size(min = 1, max = 100) List<UUID> ids) {
		return ResponseEntity.ok(userService.getListUser(ids));
	}

	@PatchMapping("/{id}/profile")
	public ResponseEntity<UserResponseDTO> updateUserProfile(@PathVariable UUID id,
			@Valid @RequestBody UpdateUserProfileRequest request) {
		return ResponseEntity.ok(userService.updateUserProfile(id, request));
	}
}
