package com.devpilot.service;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.model.enums.IndexStatus;
import com.devpilot.model.enums.RepoSource;
import com.devpilot.repository.RepoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoService {

    private final RepoRepository repoRepository;
    private final GitHubApiClient gitHubApiClient;
    private final AuthService authService;

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?github\\.com/([^/]+)/([^/\\s?#]+)/?.*"
    );

    /**
     * Add a repo by GitHub URL (public, no auth needed)
     */
    public Repository addByUrl(String githubUrl) {
        Matcher matcher = GITHUB_URL_PATTERN.matcher(githubUrl.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid GitHub URL: " + githubUrl);
        }

        String owner = matcher.group(1);
        String name = matcher.group(2).replaceAll("\\.git$", "");

        // Check if already exists
        return repoRepository.findByOwnerAndName(owner, name)
                .orElseGet(() -> {
                    JsonNode meta = gitHubApiClient.getRepoMetadata(owner, name, null);
                    return createRepoFromMetadata(meta, RepoSource.URL_ADDED, null);
                });
    }

    /**
     * Sync all repos for authenticated user
     */
    public List<Repository> syncUserRepos(User user) {
        String token = authService.getDecryptedToken(user);
        List<Repository> synced = new ArrayList<>();

        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            JsonNode repos = gitHubApiClient.getUserRepos(token, page, 100);

            if (!repos.isArray() || repos.size() == 0) {
                hasMore = false;
                continue;
            }

            for (JsonNode repoNode : repos) {
                String owner = repoNode.get("owner").get("login").asText();
                String name = repoNode.get("name").asText();

                Repository repo = repoRepository.findByOwnerAndName(owner, name)
                        .map(existing -> {
                            updateRepoFromMetadata(existing, repoNode);
                            existing.setUser(user);
                            existing.setSource(RepoSource.GITHUB_SYNC);
                            return repoRepository.save(existing);
                        })
                        .orElseGet(() -> createRepoFromMetadata(repoNode, RepoSource.GITHUB_SYNC, user));

                synced.add(repo);
            }

            page++;
            if (repos.size() < 100) hasMore = false;
        }

        return synced;
    }

    public List<Repository> getAllRepos() {
        return repoRepository.findAll();
    }

    public List<Repository> getUserRepos(Long userId) {
        return repoRepository.findByUserId(userId);
    }

    public Repository getById(Long id) {
        return repoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + id));
    }

    public void deleteRepo(Long id) {
        repoRepository.deleteById(id);
    }

    private Repository createRepoFromMetadata(JsonNode meta, RepoSource source, User user) {
        Repository repo = Repository.builder()
                .githubUrl(meta.get("html_url").asText())
                .owner(meta.get("owner").get("login").asText())
                .name(meta.get("name").asText())
                .description(meta.has("description") && !meta.get("description").isNull()
                        ? meta.get("description").asText() : null)
                .language(meta.has("language") && !meta.get("language").isNull()
                        ? meta.get("language").asText() : null)
                .defaultBranch(meta.has("default_branch")
                        ? meta.get("default_branch").asText() : "main")
                .isPrivate(meta.has("private") && meta.get("private").asBoolean())
                .source(source)
                .user(user)
                .indexStatus(IndexStatus.NEW)
                .build();

        return repoRepository.save(repo);
    }

    private void updateRepoFromMetadata(Repository repo, JsonNode meta) {
        repo.setDescription(meta.has("description") && !meta.get("description").isNull()
                ? meta.get("description").asText() : null);
        repo.setLanguage(meta.has("language") && !meta.get("language").isNull()
                ? meta.get("language").asText() : null);
        repo.setDefaultBranch(meta.has("default_branch")
                ? meta.get("default_branch").asText() : "main");
        repo.setPrivate(meta.has("private") && meta.get("private").asBoolean());
    }
}
