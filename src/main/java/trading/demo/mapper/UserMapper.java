package trading.demo.mapper;

import org.springframework.stereotype.Component;

import trading.demo.model.dto.userDto.CreateUserProfileRequest;
import trading.demo.model.dto.userDto.UpdateUserProfileRequest;
import trading.demo.model.dto.userDto.UserResponseDTO;
import trading.demo.model.entity.UserEntity;

@Component
public class UserMapper {
	public UserResponseDTO toDTO(UserEntity user) {
		UserResponseDTO dto = new UserResponseDTO();
		dto.setId(user.getId() == null ? null : user.getId().toString());
		dto.setFirstName(user.getFirstName());
		dto.setLastName(user.getLastName());
		dto.setStatus(user.getStatus());
		dto.setRole(user.getRole());
		dto.setUserType(user.getUserType());
		dto.setCreatedAt(user.getCreatedAt());
		dto.setUpdatedAt(user.getUpdatedAt());
		return dto;
	}

	public UserEntity toEntity(CreateUserProfileRequest request, UserEntity user) {
		user.setFirstName(request.getFirstName().strip());
		user.setLastName(request.getLastName().strip());
		return user;
	}

	public void updateEntity(UpdateUserProfileRequest request, UserEntity user) {
		user.setFirstName(request.getFirstName().strip());
		user.setLastName(request.getLastName().strip());
	}
}
