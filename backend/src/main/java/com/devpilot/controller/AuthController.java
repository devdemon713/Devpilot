package com.devpilot.controller;

import com.devpilot.dto.AuthDTO;
import com.devpilot.model.User;
import com.devpilot.service.AuthService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Redirect to GitHub OAuth login page.
     */
    @GetMapping("/github")
    public ResponseEntity<Void> githubLogin() {
        String authUrl = authService.getGitHubAuthUrl();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authUrl))
                .build();
    }

    /**
     * GitHub OAuth callback — exchanges code for token, creates/updates user.
     */
    @GetMapping("/github/callback")
    public ResponseEntity<Void> githubCallback(
            @RequestParam String code,
            HttpSession session
    ) {
        authService.handleGitHubCallback(code, session);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/dashboard/repositories"))
                .build();
    }

    /**
     * Get current authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        User user = authService.getCurrentUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok(AuthDTO.toResponse(user));
    }

    /**
     * Logout — invalidate session.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.ok().build();
    }
}
