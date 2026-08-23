package com.devpilot.service;

import com.devpilot.dto.AuthDTO;
import com.devpilot.model.User;
import com.devpilot.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.github.client-secret}")
    private String clientSecret;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public String getGitHubAuthUrl() {
        return "https://github.com/login/oauth/authorize?client_id=" + clientId
                + "&scope=read:user,repo"
                + "&redirect_uri=http://localhost:8080/api/auth/github/callback";
    }

    public User handleGitHubCallback(String code, HttpSession session) {
        // Exchange code for access token
        String accessToken = exchangeCodeForToken(code);

        // Fetch user info from GitHub
        JsonNode userInfo = fetchGitHubUser(accessToken);

        Long githubId = userInfo.get("id").asLong();
        String username = userInfo.get("login").asText();
        String name = userInfo.has("name") && !userInfo.get("name").isNull()
                ? userInfo.get("name").asText() : username;
        String email = userInfo.has("email") && !userInfo.get("email").isNull()
                ? userInfo.get("email").asText() : null;
        String avatarUrl = userInfo.has("avatar_url")
                ? userInfo.get("avatar_url").asText() : null;

        // Find or create user
        User user = userRepository.findByGithubId(githubId)
                .map(existing -> {
                    existing.setUsername(username);
                    existing.setName(name);
                    existing.setEmail(email);
                    existing.setAvatarUrl(avatarUrl);
                    existing.setEncryptedAccessToken(encryptionService.encrypt(accessToken));
                    existing.setLastLoginAt(java.time.LocalDateTime.now());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .githubId(githubId)
                            .username(username)
                            .name(name)
                            .email(email)
                            .avatarUrl(avatarUrl)
                            .encryptedAccessToken(encryptionService.encrypt(accessToken))
                            .build();
                    return userRepository.save(newUser);
                });

        // Store user ID in session
        session.setAttribute("userId", user.getId());

        return user;
    }

    private String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://github.com/login/oauth/access_token", request, String.class);

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            if (json.has("error")) {
                throw new RuntimeException("GitHub OAuth error: " + json.get("error_description").asText());
            }
            return json.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse GitHub token response", e);
        }
    }

    private JsonNode fetchGitHubUser(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "https://api.github.com/user", HttpMethod.GET, request, String.class);

        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch GitHub user", e);
        }
    }

    public User getCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return null;
        return userRepository.findById(userId).orElse(null);
    }

    public String getDecryptedToken(User user) {
        return encryptionService.decrypt(user.getEncryptedAccessToken());
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }
}
