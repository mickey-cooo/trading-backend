package trading.demo.controller;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import trading.demo.model.entity.UserEntity;
import trading.demo.service.UserService;

@RestController
@Validated
@RequestMapping("/users")
public class UserController {
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	// the caller's own data: the id always comes from the token, never the URL
	@GetMapping("/me")
	public ResponseEntity<UserResponseDTO> getMe(@AuthenticationPrincipal UserEntity me) {
		return ResponseEntity.ok(userService.getUserById(me.getId()));
	}

	@PostMapping("/me/profile")
	public ResponseEntity<UserResponseDTO> createMyProfile(@AuthenticationPrincipal UserEntity me,
			@Valid @RequestBody CreateUserProfileRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUserProfile(me.getId(), request));
	}

	@PatchMapping("/me")
	public ResponseEntity<UserResponseDTO> updateMe(@AuthenticationPrincipal UserEntity me,
			@Valid @RequestBody UpdateUserProfileRequest request) {
		return ResponseEntity.ok(userService.updateUserProfile(me.getId(), request));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<UserResponseDTO> getUserById(@PathVariable UUID id) {
		return ResponseEntity.ok(userService.getUserById(id));
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<UserResponseDTO>> getListUser(@RequestParam @Size(min = 1, max = 100) List<UUID> ids) {
		return ResponseEntity.ok(userService.getListUser(ids));
	}
}
