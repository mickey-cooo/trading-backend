package trading.demo.mapper;

import org.springframework.stereotype.Component;

import trading.demo.model.dto.userDto.CreateUserProfileRequest;
import trading.demo.model.dto.userDto.UserResponseDTO;
import trading.demo.model.entity.UserEntity;

@Component
public class UserMapper {
    public UserResponseDTO toDTO(UserEntity user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        return dto;
    }

    public UserEntity toEntity(CreateUserProfileRequest request) {
        UserEntity user = new UserEntity();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        return user;
    }
}
