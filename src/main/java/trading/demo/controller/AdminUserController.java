package trading.demo.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.adminDto.ChangeStatusRequest;
import trading.demo.model.dto.userDto.UserResponseDTO;
import trading.demo.model.entity.UserEntity;
import trading.demo.service.AdminService;

// Only an ADMIN can suspend or reactivate a user.
@RestController
@Validated
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
	private final AdminService adminService;

	public AdminUserController(AdminService adminService) {
		this.adminService = adminService;
	}

	@PatchMapping("/{id}/status")
	public UserResponseDTO changeStatus(@AuthenticationPrincipal UserEntity admin, @PathVariable UUID id,
			@Valid @RequestBody ChangeStatusRequest request) {
		return adminService.changeUserStatus(admin.getId(), id, request.status());
	}
}
