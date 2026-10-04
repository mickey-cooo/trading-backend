package trading.demo.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trading.demo.exception.ResourceNotFoundException;
import trading.demo.mapper.UserMapper;
import trading.demo.model.dto.userDto.CreateUserProfileRequest;
import trading.demo.model.dto.userDto.UpdateUserProfileRequest;
import trading.demo.model.dto.userDto.UserResponseDTO;
import trading.demo.model.enums.RoleType;
import trading.demo.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService {
	private final UserRepository userRepository;
	private final UserMapper userMapper;

	public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
	}

	@Override
	@Transactional
	public UserResponseDTO createUserProfile(UUID id, CreateUserProfileRequest request) {
		var user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
		userMapper.toEntity(request, user);

		if (user.getCreatedAt() == null) {
			user.setCreatedAt(Instant.now());
		}
		if (user.getRole() == null) {
			user.setRole(RoleType.USER);
		}
		return userMapper.toDTO(userRepository.save(user));
	}

	@Override
	public UserResponseDTO getUserById(UUID id) {
		return userRepository.findById(id).map(userMapper::toDTO)
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	@Override
	public List<UserResponseDTO> getListUser(List<UUID> ids) {
		return userRepository.findAllById(ids).stream().map(userMapper::toDTO).toList();
	}

	@Override
	@Transactional
	public UserResponseDTO updateUserProfile(UUID id, UpdateUserProfileRequest request) {
		var user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
		userMapper.updateEntity(request, user);
		return userMapper.toDTO(userRepository.save(user));
	}
}
