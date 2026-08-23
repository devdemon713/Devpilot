package com.devpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class GitHubApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.github.token:}")
    private String serverGitHubToken;

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (token != null && !token.isEmpty()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    /**
     * Fetch repo metadata: GET /repos/{owner}/{repo}
     */
    public JsonNode getRepoMetadata(String owner, String repo, String token) {
        String url = String.format("https://api.github.com/repos/%s/%s", owner, repo);
        String useToken = (token != null && !token.isEmpty()) ? token : serverGitHubToken;

        HttpEntity<Void> request = new HttpEntity<>(createHeaders(useToken));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse repo metadata", e);
        }
    }

    /**
     * Fetch user repos: GET /user/repos
     */
    public JsonNode getUserRepos(String token, int page, int perPage) {
        String url = String.format("https://api.github.com/user/repos?page=%d&per_page=%d&sort=updated&affiliation=owner", page, perPage);

        HttpEntity<Void> request = new HttpEntity<>(createHeaders(token));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user repos", e);
        }
    }

    /**
     * Fetch repo tree: GET /repos/{owner}/{repo}/git/trees/{branch}?recursive=1
     */
    public JsonNode getRepoTree(String owner, String repo, String branch, String token) {
        String url = String.format("https://api.github.com/repos/%s/%s/git/trees/%s?recursive=1", owner, repo, branch);
        String useToken = (token != null && !token.isEmpty()) ? token : serverGitHubToken;

        HttpEntity<Void> request = new HttpEntity<>(createHeaders(useToken));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch repo tree", e);
        }
    }

    /**
     * Fetch file content: GET /repos/{owner}/{repo}/contents/{path}
     * Returns the decoded file content as a string.
     */
    public String getFileContent(String owner, String repo, String path, String branch, String token) {
        String url = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s", owner, repo, path, branch);
        String useToken = (token != null && !token.isEmpty()) ? token : serverGitHubToken;

        HttpHeaders headers = createHeaders(useToken);
        headers.setAccept(List.of(MediaType.valueOf("application/vnd.github.v3.raw")));

        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch file content: {}/{} - {}", repo, path, e.getMessage());
            return null;
        }
    }
}
