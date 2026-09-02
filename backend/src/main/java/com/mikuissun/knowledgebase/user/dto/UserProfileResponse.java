package com.mikuissun.knowledgebase.user.dto;

import com.mikuissun.knowledgebase.user.entity.User;

public record UserProfileResponse(Long id, String username, String nickname, String email) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getNickname(), user.getEmail());
    }
}
