package com.mikuissun.knowledgebase.user.dto;

public record AuthResponse(String token, UserProfileResponse user) {
}
