package com.devpilot.dto;

import lombok.*;

public class AuthDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserResponse {
        private Long id;
        private Long githubId;
        private String username;
        private String name;
        private String email;
        private String avatarUrl;
    }

    public static UserResponse toResponse(com.devpilot.model.User user) {
        return UserResponse.builder()
                .id(user.getId())
                .githubId(user.getGithubId())
                .username(user.getUsername())
                .name(user.getName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
