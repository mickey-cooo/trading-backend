package trading.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import trading.demo.exception.ResourceNotFoundException;
import trading.demo.model.dto.userDto.GetUser;
import trading.demo.model.entity.UserEntity;
import trading.demo.repository.UserRepository;

@Service
public class UserServiceImpl {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserEntity getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

}