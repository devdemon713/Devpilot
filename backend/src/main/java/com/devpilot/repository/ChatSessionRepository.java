package com.devpilot.repository;

import com.devpilot.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByRepositoryIdOrderByUpdatedAtDesc(Long repoId);
    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
