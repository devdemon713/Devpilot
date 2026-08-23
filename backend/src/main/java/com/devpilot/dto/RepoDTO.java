package com.devpilot.dto;

import com.devpilot.model.enums.IndexStatus;
import com.devpilot.model.enums.RepoSource;
import lombok.*;
import java.time.LocalDateTime;

public class RepoDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddByUrlRequest {
        private String githubUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String githubUrl;
        private String owner;
        private String name;
        private String description;
        private String language;
        private String defaultBranch;
        private boolean isPrivate;
        private RepoSource source;
        private IndexStatus indexStatus;
        private int filesTotal;
        private int filesProcessed;
        private int chunkCount;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime indexedAt;
    }

    public static Response toResponse(com.devpilot.model.Repository repo) {
        return Response.builder()
                .id(repo.getId())
                .githubUrl(repo.getGithubUrl())
                .owner(repo.getOwner())
                .name(repo.getName())
                .description(repo.getDescription())
                .language(repo.getLanguage())
                .defaultBranch(repo.getDefaultBranch())
                .isPrivate(repo.isPrivate())
                .source(repo.getSource())
                .indexStatus(repo.getIndexStatus())
                .filesTotal(repo.getFilesTotal())
                .filesProcessed(repo.getFilesProcessed())
                .chunkCount(repo.getChunkCount())
                .errorMessage(repo.getErrorMessage())
                .createdAt(repo.getCreatedAt())
                .indexedAt(repo.getIndexedAt())
                .build();
    }
}
