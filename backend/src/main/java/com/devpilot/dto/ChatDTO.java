package com.devpilot.dto;

import com.devpilot.model.enums.MessageRole;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class ChatDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSessionRequest {
        private Long repoId;
        private String title;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResponse {
        private Long id;
        private String title;
        private Long repoId;
        private String repoName;
        private String repoOwner;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private int messageCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private Long id;
        private MessageRole role;
        private String content;
        private String citations;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamEvent {
        private String type; // "token", "citation", "done", "error"
        private String content;
    }
}
