package com.devpilot.service;

import com.devpilot.dto.ChatDTO;
import com.devpilot.model.ChatMessage;
import com.devpilot.model.ChatSession;
import com.devpilot.model.Repository;
import com.devpilot.model.enums.MessageRole;
import com.devpilot.repository.ChatMessageRepository;
import com.devpilot.repository.ChatSessionRepository;
import com.devpilot.repository.RepoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RepoRepository repoRepository;
    private final VectorSearchService vectorSearchService;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatSession createSession(Long repoId, String title) {
        Repository repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repoId));

        ChatSession session = ChatSession.builder()
                .title(title != null ? title : "New Chat")
                .repository(repo)
                .build();

        return chatSessionRepository.save(session);
    }

    public List<ChatDTO.SessionResponse> getSessionsByRepo(Long repoId) {
        return chatSessionRepository.findByRepositoryIdOrderByUpdatedAtDesc(repoId)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    public List<ChatDTO.MessageResponse> getMessages(Long sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    /**
     * Send a message and stream the AI response via SSE.
     */
    public Flux<ChatDTO.StreamEvent> sendMessageStreaming(Long sessionId, String userMessage) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        Repository repo = session.getRepository();

        // Save user message
        ChatMessage userMsg = ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(userMessage)
                .build();
        chatMessageRepository.save(userMsg);

        // Get conversation history (last 10 messages)
        List<ChatMessage> history = chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatMessage> recentHistory = history.size() > 10
                ? history.subList(history.size() - 10, history.size())
                : history;

        // RAG: Search for relevant code context
        List<Document> contextDocs = vectorSearchService.searchRepoContext(
                repo.getId(), userMessage, 8);

        // Build the context string
        String codeContext = contextDocs.stream()
                .map(doc -> {
                    String filePath = doc.getMetadata().getOrDefault("filePath", "unknown").toString();
                    String startLine = doc.getMetadata().getOrDefault("startLine", "?").toString();
                    String endLine = doc.getMetadata().getOrDefault("endLine", "?").toString();
                    return String.format("--- File: %s (lines %s-%s) ---\n%s",
                            filePath, startLine, endLine, doc.getText());
                })
                .collect(Collectors.joining("\n\n"));

        // Build citations JSON
        String citationsJson;
        try {
            var citations = contextDocs.stream()
                    .map(doc -> {
                        var meta = doc.getMetadata();
                        return java.util.Map.of(
                                "filePath", meta.getOrDefault("filePath", "unknown"),
                                "startLine", meta.getOrDefault("startLine", 0),
                                "endLine", meta.getOrDefault("endLine", 0)
                        );
                    })
                    .toList();
            citationsJson = objectMapper.writeValueAsString(citations);
        } catch (Exception e) {
            citationsJson = "[]";
        }

        // Build conversation history string
        String historyStr = recentHistory.stream()
                .filter(m -> m.getId() != null && !m.getId().equals(userMsg.getId()))
                .map(m -> m.getRole().name() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        // System prompt
        String systemPrompt = String.format("""
                You are DevPilot, an expert AI coding assistant. You are helping the user understand and work with the codebase of the GitHub repository "%s/%s".
                
                You have access to relevant code snippets from the repository retrieved via semantic search. Use these snippets to provide accurate, contextual answers.
                
                Guidelines:
                - Reference specific files and line numbers when discussing code
                - If the retrieved context doesn't contain enough information, say so honestly
                - Provide code examples when helpful
                - Use markdown formatting for code blocks, lists, and headers
                - Be concise but thorough
                
                Retrieved Code Context:
                %s
                """, repo.getOwner(), repo.getName(), codeContext);

        // Build the prompt with history
        String fullPrompt = historyStr.isEmpty()
                ? userMessage
                : historyStr + "\nUSER: " + userMessage;

        // Stream the response
        final String citations = citationsJson;

        ChatClient chatClient = chatClientBuilder.build();

        StringBuilder fullResponse = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(fullPrompt)
                .stream() 
                .content()
                .map(token -> {
                    fullResponse.append(token);
                    return ChatDTO.StreamEvent.builder()
                            .type("token")
                            .content(token)
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    // Save assistant message after stream completes
                    ChatMessage assistantMsg = ChatMessage.builder()
                            .session(session)
                            .role(MessageRole.ASSISTANT)
                            .content(fullResponse.toString())
                            .citations(citations)
                            .build();
                    chatMessageRepository.save(assistantMsg);

                    // Update session title if it's the first message
                    if (history.size() <= 1) {
                        String title = userMessage.length() > 50
                                ? userMessage.substring(0, 50) + "..."
                                : userMessage;
                        session.setTitle(title);
                        chatSessionRepository.save(session);
                    }

                    return Flux.just(
                            ChatDTO.StreamEvent.builder()
                                    .type("citation")
                                    .content(citations)
                                    .build(),
                            ChatDTO.StreamEvent.builder()
                                    .type("done")
                                    .content("")
                                    .build()
                    );
                }))
                .onErrorResume(e -> {
                    log.error("Chat streaming error: {}", e.getMessage());
                    return Flux.just(ChatDTO.StreamEvent.builder()
                            .type("error")
                            .content("An error occurred: " + e.getMessage())
                            .build());
                });
    }

    private ChatDTO.SessionResponse toSessionResponse(ChatSession session) {
        return ChatDTO.SessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .repoId(session.getRepository().getId())
                .repoName(session.getRepository().getName())
                .repoOwner(session.getRepository().getOwner())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .messageCount(session.getMessages().size())
                .build();
    }

    private ChatDTO.MessageResponse toMessageResponse(ChatMessage message) {
        return ChatDTO.MessageResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .citations(message.getCitations())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
