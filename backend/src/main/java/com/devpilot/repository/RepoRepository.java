package com.devpilot.repository;

import com.devpilot.model.Repository;
import com.devpilot.model.enums.IndexStatus;
import com.devpilot.model.enums.RepoSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RepoRepository extends JpaRepository<Repository, Long> {
    List<Repository> findByUserId(Long userId);
    List<Repository> findByUserIdAndSource(Long userId, RepoSource source);
    List<Repository> findBySource(RepoSource source);
    List<Repository> findByIndexStatus(IndexStatus status);
    Optional<Repository> findByOwnerAndName(String owner, String name);
    List<Repository> findByUserIdOrSource(Long userId, RepoSource source);
}
