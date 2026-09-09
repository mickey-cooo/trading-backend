package trading.demo.service;

import org.springframework.stereotype.Service;

import trading.demo.exception.ResourceNotFoundException;
import trading.demo.model.entity.UserEntity;
import trading.demo.repository.UserRepository;

@Service
public class UserServiceImpl {

    private UserRepository userRepository;

    public UserEntity createUser(UserEntity user) {
        return userRepository.save(user);
    }

    public UserEntity getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public UserEntity getListUser(Long[] ids) {
        return userRepository.findAll(ids).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public UserEntity updateUser(Long id, UserEntity user) {
        UserEntity existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        existingUser.setFirstName(user.getFirstName());
        existingUser.setLastName(user.getLastName());
        existingUser.setEmail(user.getEmail());
        return userRepository.save(existingUser);
    }

}