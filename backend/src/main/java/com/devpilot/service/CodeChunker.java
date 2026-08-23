package com.devpilot.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class CodeChunker {

    private static final int MAX_CHUNK_SIZE = 1500; // characters
    private static final int OVERLAP_SIZE = 200;    // characters overlap between chunks

    /**
     * Chunk a code file into overlapping segments.
     * Each chunk includes metadata about the file path and line range.
     */
    public List<CodeChunk> chunkFile(String filePath, String content) {
        List<CodeChunk> chunks = new ArrayList<>();

        if (content == null || content.isBlank()) return chunks;

        // If file is small enough, keep as single chunk
        if (content.length() <= MAX_CHUNK_SIZE) {
            chunks.add(new CodeChunk(
                    filePath,
                    content,
                    1,
                    countLines(content)
            ));
            return chunks;
        }

        // Split into overlapping chunks
        String[] lines = content.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        int startLine = 1;
        int currentLine = 1;

        for (String line : lines) {
            if (currentChunk.length() + line.length() + 1 > MAX_CHUNK_SIZE && currentChunk.length() > 0) {
                // Save current chunk
                chunks.add(new CodeChunk(
                        filePath,
                        currentChunk.toString(),
                        startLine,
                        currentLine - 1
                ));

                // Start new chunk with overlap
                String chunkText = currentChunk.toString();
                int overlapStart = Math.max(0, chunkText.length() - OVERLAP_SIZE);
                String overlap = chunkText.substring(overlapStart);

                // Count lines in overlap to adjust startLine
                int overlapLines = countLines(overlap);

                currentChunk = new StringBuilder(overlap);
                startLine = currentLine - overlapLines;
            }

            currentChunk.append(line).append("\n");
            currentLine++;
        }

        // Don't forget the last chunk
        if (currentChunk.length() > 0) {
            chunks.add(new CodeChunk(
                    filePath,
                    currentChunk.toString(),
                    startLine,
                    currentLine - 1
            ));
        }

        return chunks;
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\n", -1).length;
    }

    /**
     * Represents a chunk of code with metadata.
     */
    public record CodeChunk(
            String filePath,
            String content,
            int startLine,
            int endLine
    ) {
        public String toEmbeddingText() {
            return String.format("File: %s (lines %d-%d)\n\n%s", filePath, startLine, endLine, content);
        }
    }
}
