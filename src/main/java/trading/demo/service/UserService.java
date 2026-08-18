package trading.demo.service;

import trading.demo.model.entity.UserEntity;

public interface UserService {
    UserEntity getUserById(Long id);
}