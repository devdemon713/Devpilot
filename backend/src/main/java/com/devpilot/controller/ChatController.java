package com.devpilot.controller;

import com.devpilot.dto.ChatDTO;
import com.devpilot.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create a new chat session for a repository.
     */
    @PostMapping("/sessions")
    public ResponseEntity<ChatDTO.SessionResponse> createSession(
            @RequestBody ChatDTO.CreateSessionRequest request
    ) {
        var session = chatService.createSession(request.getRepoId(), request.getTitle());
        return ResponseEntity.ok(ChatDTO.SessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .repoId(session.getRepository().getId())
                .repoName(session.getRepository().getName())
                .repoOwner(session.getRepository().getOwner())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .messageCount(0)
                .build());
    }

    /**
     * Get all chat sessions for a repository.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatDTO.SessionResponse>> getSessions(
            @RequestParam Long repoId
    ) {
        return ResponseEntity.ok(chatService.getSessionsByRepo(repoId));
    }

    /**
     * Get messages for a chat session.
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatDTO.MessageResponse>> getMessages(
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(chatService.getMessages(sessionId));
    }

    /**
     * Send a message and stream the AI response via SSE.
     */
    @PostMapping(value = "/sessions/{sessionId}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody ChatDTO.SendMessageRequest request
    ) {
        return chatService.sendMessageStreaming(sessionId, request.getContent())
                .map(event -> {
                    try {
                        return objectMapper.writeValueAsString(event);
                    } catch (Exception e) {
                        return "{\"type\":\"error\",\"content\":\"Serialization error\"}";
                    }
                });
    }
}
