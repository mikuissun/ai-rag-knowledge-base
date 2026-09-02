package com.mikuissun.knowledgebase.auth;

import com.mikuissun.knowledgebase.common.api.ApiResponse;
import com.mikuissun.knowledgebase.user.dto.AuthResponse;
import com.mikuissun.knowledgebase.user.dto.LoginRequest;
import com.mikuissun.knowledgebase.user.dto.RegisterRequest;
import com.mikuissun.knowledgebase.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
