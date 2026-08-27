package trading.demo.service;

import trading.demo.model.entity.UserEntity;

public interface UserService {
    UserEntity getUserById(String id);

    UserEntity updateUserProfile(String id);
}