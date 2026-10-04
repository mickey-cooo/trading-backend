package trading.demo.service;

import java.util.List;
import java.util.UUID;
import trading.demo.model.dto.userDto.CreateUserProfileRequest;
import trading.demo.model.dto.userDto.UpdateUserProfileRequest;
import trading.demo.model.dto.userDto.UserResponseDTO;

public interface UserService {
	UserResponseDTO createUserProfile(UUID id, CreateUserProfileRequest request);

	UserResponseDTO getUserById(UUID id);

	List<UserResponseDTO> getListUser(List<UUID> ids);

	UserResponseDTO updateUserProfile(UUID id, UpdateUserProfileRequest request);
}
