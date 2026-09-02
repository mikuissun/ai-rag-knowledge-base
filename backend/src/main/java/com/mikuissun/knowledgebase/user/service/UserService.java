package com.mikuissun.knowledgebase.user.service;

import com.mikuissun.knowledgebase.user.entity.User;

import java.util.Optional;

public interface UserService {

    User register(String username, String rawPassword, String nickname, String email);

    Optional<User> findByUsername(String username);
}
