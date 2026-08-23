package com.devpilot.service;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.model.enums.IndexStatus;
import com.devpilot.repository.RepoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingService {

    private final RepoRepository repoRepository;
    private final GitHubApiClient gitHubApiClient;
    private final CodeFileFilter codeFileFilter;
    private final CodeChunker codeChunker;
    private final VectorStore vectorStore;
    private final AuthService authService;

    /**
     * Trigger indexing for a repository. Runs asynchronously.
     */
    public void startIndexing(Long repoId) {
        Repository repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repoId));

        // Reset progress
        repo.setIndexStatus(IndexStatus.INDEXING);
        repo.setFilesTotal(0);
        repo.setFilesProcessed(0);
        repo.setChunkCount(0);
        repo.setErrorMessage(null);
        repo.setIndexedAt(null);
        repoRepository.save(repo);

        // Start async indexing
        indexAsync(repoId);
    }

    @Async("indexingExecutor")
    public void indexAsync(Long repoId) {
        Repository repo = repoRepository.findById(repoId).orElse(null);
        if (repo == null) return;

        try {
            doIndex(repo);
        } catch (Exception e) {
            log.error("Indexing failed for repo {}: {}", repoId, e.getMessage(), e);
            markFailed(repo, e.getMessage());
        }
    }

    private void doIndex(Repository repo) {
        log.info("Starting indexing for {}/{}", repo.getOwner(), repo.getName());

        // Determine which token to use
        String token = null;
        if (repo.getUser() != null) {
            try {
                token = authService.getDecryptedToken(repo.getUser());
            } catch (Exception e) {
                log.warn("Could not decrypt user token, using server token");
            }
        }

        // Step 1: Delete existing vectors for this repo
        deleteExistingVectors(repo.getId());

        // Step 2: Fetch repository tree
        JsonNode tree = gitHubApiClient.getRepoTree(
                repo.getOwner(), repo.getName(), repo.getDefaultBranch(), token);

        // Step 3: Filter indexable files
        List<String> indexableFiles = codeFileFilter.filterIndexableFiles(tree);
        repo.setFilesTotal(indexableFiles.size());
        repoRepository.save(repo);

        log.info("Found {} indexable files in {}/{}", indexableFiles.size(), repo.getOwner(), repo.getName());

        // Step 4: Process each file
        List<Document> batch = new ArrayList<>();
        int filesProcessed = 0;
        int totalChunks = 0;

        for (String filePath : indexableFiles) {
            try {
                // Fetch file content
                String content = gitHubApiClient.getFileContent(
                        repo.getOwner(), repo.getName(), filePath, repo.getDefaultBranch(), token);

                if (content == null || content.isBlank()) {
                    filesProcessed++;
                    continue;
                }

                // Chunk the file
                List<CodeChunker.CodeChunk> chunks = codeChunker.chunkFile(filePath, content);

                for (CodeChunker.CodeChunk chunk : chunks) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("repoId", repo.getId().toString());
                    metadata.put("repoName", repo.getOwner() + "/" + repo.getName());
                    metadata.put("filePath", chunk.filePath());
                    metadata.put("startLine", chunk.startLine());
                    metadata.put("endLine", chunk.endLine());
                    metadata.put("language", repo.getLanguage() != null ? repo.getLanguage() : "unknown");

                    Document doc = new Document(chunk.toEmbeddingText(), metadata);
                    batch.add(doc);
                    totalChunks++;
                }

                filesProcessed++;

                // Batch add to vector store every 32 documents
                if (batch.size() >= 32) {
                    vectorStore.add(batch);
                    batch.clear();
                }

                // Update progress every 5 files
                if (filesProcessed % 5 == 0) {
                    repo.setFilesProcessed(filesProcessed);
                    repo.setChunkCount(totalChunks);
                    repoRepository.save(repo);
                }

                // Rate limiting: small delay between API calls
                if (filesProcessed % 10 == 0) {
                    Thread.sleep(500);
                }

            } catch (Exception e) {
                log.warn("Error processing file {}: {}", filePath, e.getMessage());
                filesProcessed++;
                // Continue with next file
            }
        }

        // Add remaining batch
        if (!batch.isEmpty()) {
            vectorStore.add(batch);
        }

        // Mark as ready
        repo.setIndexStatus(IndexStatus.READY);
        repo.setFilesProcessed(filesProcessed);
        repo.setChunkCount(totalChunks);
        repo.setIndexedAt(LocalDateTime.now());
        repoRepository.save(repo);

        log.info("Indexing complete for {}/{}: {} files, {} chunks",
                repo.getOwner(), repo.getName(), filesProcessed, totalChunks);
    }

    private void deleteExistingVectors(Long repoId) {
        try {
            // Search for existing docs with this repoId and delete them
            var results = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query("repo")
                            .topK(1000)
                            .filterExpression("repoId == '" + repoId + "'")
                            .build()
            );

            if (results != null && !results.isEmpty()) {
                List<String> ids = results.stream().map(Document::getId).toList();
                vectorStore.delete(ids);
                log.info("Deleted {} existing vectors for repo {}", ids.size(), repoId);
            }
        } catch (Exception e) {
            log.warn("Could not delete existing vectors: {}", e.getMessage());
        }
    }

    private void markFailed(Repository repo, String error) {
        repo.setIndexStatus(IndexStatus.FAILED);
        repo.setErrorMessage(error != null ? error.substring(0, Math.min(error.length(), 500)) : "Unknown error");
        repoRepository.save(repo);
    }
}
