package com.devpilot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {

    private final VectorStore vectorStore;

    /**
     * Search for relevant code chunks for a given query within a specific repository.
     */
    public List<Document> searchRepoContext(Long repoId, String query, int topK) {
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(0.5)
                    .filterExpression("repoId == '" + repoId + "'")
                    .build();

            return vectorStore.similaritySearch(searchRequest);
        } catch (Exception e) {
            log.error("Vector search failed for repo {}: {}", repoId, e.getMessage());
            return List.of();
        }
    }
}
