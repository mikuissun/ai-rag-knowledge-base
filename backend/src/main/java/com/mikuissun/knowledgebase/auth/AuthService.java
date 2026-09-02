package com.mikuissun.knowledgebase.auth;

import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.user.dto.AuthResponse;
import com.mikuissun.knowledgebase.user.dto.LoginRequest;
import com.mikuissun.knowledgebase.user.dto.RegisterRequest;
import com.mikuissun.knowledgebase.user.dto.UserProfileResponse;
import com.mikuissun.knowledgebase.user.entity.User;
import com.mikuissun.knowledgebase.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public UserProfileResponse register(RegisterRequest request) {
        User user = userService.register(request.username(), request.password(), request.nickname(), request.email());
        return UserProfileResponse.from(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userService.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
        return new AuthResponse(jwtTokenService.generateToken(user), UserProfileResponse.from(user));
    }
}
