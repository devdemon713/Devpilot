package com.devpilot.controller;

import com.devpilot.dto.RepoDTO;
import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.service.AuthService;
import com.devpilot.service.IndexingService;
import com.devpilot.service.RepoService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;
    private final IndexingService indexingService;
    private final AuthService authService;

    /**
     * Add a repo by GitHub URL (no auth needed for public repos).
     */
    @PostMapping("/url")
    public ResponseEntity<?> addByUrl(@RequestBody RepoDTO.AddByUrlRequest request) {
        try {
            Repository repo = repoService.addByUrl(request.getGithubUrl());
            return ResponseEntity.ok(RepoDTO.toResponse(repo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch repository: " + e.getMessage()));
        }
    }

    /**
     * Sync all repos for the authenticated user (requires login).
     */
    @PostMapping("/sync")
    public ResponseEntity<?> syncRepos(HttpSession session) {
        User user = authService.getCurrentUser(session);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated. Please login with GitHub first."));
        }

        List<Repository> repos = repoService.syncUserRepos(user);
        return ResponseEntity.ok(repos.stream().map(RepoDTO::toResponse).toList());
    }

    /**
     * Get all repos (user's repos if logged in + URL-added repos).
     */
    @GetMapping
    public ResponseEntity<List<RepoDTO.Response>> getAllRepos(HttpSession session) {
        User user = authService.getCurrentUser(session);
        List<Repository> repos;

        if (user != null) {
            repos = repoService.getAllRepos();
        } else {
            repos = repoService.getAllRepos();
        }

        return ResponseEntity.ok(repos.stream().map(RepoDTO::toResponse).toList());
    }

    /**
     * Get a specific repo by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RepoDTO.Response> getRepo(@PathVariable Long id) {
        Repository repo = repoService.getById(id);
        return ResponseEntity.ok(RepoDTO.toResponse(repo));
    }

    /**
     * Trigger indexing for a repository.
     */
    @PostMapping("/{id}/index")
    public ResponseEntity<RepoDTO.Response> indexRepo(@PathVariable Long id) {
        indexingService.startIndexing(id);
        Repository repo = repoService.getById(id);
        return ResponseEntity.ok(RepoDTO.toResponse(repo));
    }

    /**
     * Delete a repository.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepo(@PathVariable Long id) {
        repoService.deleteRepo(id);
        return ResponseEntity.ok().build();
    }
}
