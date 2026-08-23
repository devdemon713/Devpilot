package com.devpilot.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CodeFileFilter {

    private static final Set<String> SKIP_DIRECTORIES = Set.of(
            "node_modules", ".git", ".github", ".vscode", ".idea",
            "dist", "build", "target", "out", "bin", "obj",
            "__pycache__", ".pytest_cache", ".tox", "venv", "env",
            ".next", ".nuxt", "vendor", "bower_components",
            "coverage", ".nyc_output", ".cache", ".parcel-cache"
    );

    private static final Set<String> SKIP_EXTENSIONS = Set.of(
            // Images
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".bmp", ".webp", ".avif",
            // Fonts
            ".woff", ".woff2", ".ttf", ".eot", ".otf",
            // Binary
            ".exe", ".dll", ".so", ".dylib", ".class", ".jar", ".war",
            ".zip", ".tar", ".gz", ".rar", ".7z",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt",
            // Media
            ".mp3", ".mp4", ".avi", ".mov", ".wav", ".flac",
            // Data
            ".sqlite", ".db", ".mdb",
            // Lock files
            ".lock"
    );

    private static final Set<String> SKIP_FILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            "composer.lock", "Gemfile.lock", "Pipfile.lock",
            "poetry.lock", "cargo.lock", "go.sum",
            ".DS_Store", "Thumbs.db", ".gitignore", ".gitattributes",
            ".editorconfig", ".prettierrc", ".eslintignore",
            "LICENSE", "LICENSE.md", "LICENSE.txt"
    );

    private static final long MAX_FILE_SIZE = 100_000; // 100KB

    /**
     * Check if a file path from the repo tree should be indexed.
     */
    public boolean isIndexable(String path, long size) {
        if (size > MAX_FILE_SIZE) return false;

        String fileName = getFileName(path);
        String extension = getExtension(fileName);

        // Skip known non-code files
        if (SKIP_FILES.contains(fileName)) return false;
        if (extension != null && SKIP_EXTENSIONS.contains(extension.toLowerCase())) return false;

        // Skip files in excluded directories
        String[] parts = path.split("/");
        for (String part : parts) {
            if (SKIP_DIRECTORIES.contains(part)) return false;
        }

        return true;
    }

    /**
     * Get a list of indexable file paths from the repo tree.
     */
    public List<String> filterIndexableFiles(com.fasterxml.jackson.databind.JsonNode treeResponse) {
        List<String> indexable = new ArrayList<>();

        if (!treeResponse.has("tree")) return indexable;

        for (var node : treeResponse.get("tree")) {
            String type = node.get("type").asText();
            if (!"blob".equals(type)) continue;

            String path = node.get("path").asText();
            long size = node.has("size") ? node.get("size").asLong() : 0;

            if (isIndexable(path, size)) {
                indexable.add(path);
            }
        }

        return indexable;
    }

    private String getFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(lastDot) : null;
    }
}
