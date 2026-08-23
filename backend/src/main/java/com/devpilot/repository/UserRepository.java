package com.devpilot.repository;

import com.devpilot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGithubId(Long githubId);
    Optional<User> findByUsername(String username);
}
