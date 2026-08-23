package com.devpilot.model;

import com.devpilot.model.enums.IndexStatus;
import com.devpilot.model.enums.RepoSource;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repositories")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String githubUrl;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String name;

    private String description;

    private String language;

    @Column(nullable = false)
    private String defaultBranch;

    private boolean isPrivate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RepoSource source = RepoSource.URL_ADDED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Indexing progress fields
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IndexStatus indexStatus = IndexStatus.NEW;

    @Builder.Default
    private int filesTotal = 0;

    @Builder.Default
    private int filesProcessed = 0;

    @Builder.Default
    private int chunkCount = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime indexedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
