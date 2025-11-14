package com.alias.rag.dev.tech.trigger.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class GitUtils {

    @Value("${repo.base-path}")
    private String repoBasePath;

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }

    public void saveIndexedCommit(String repoName, String commit) {
        try {
            Path metaFile = Paths.get(repoBasePath, repoName, ".rag-meta.json");

            Map<String, Object> meta = new HashMap<>();
            meta.put("lastIndexedCommit", commit);
            meta.put("updatedAt", System.currentTimeMillis());

            mapper.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), meta);

            log.info("Commit saved for {}: {}", repoName, commit);

        } catch (Exception e) {
            log.error("Failed to save commit for {}: {}", repoName, e.getMessage());
        }
    }

    public String loadIndexedCommit(String repoName) {
        try {
            Path metaFile = Paths.get(repoBasePath, repoName, ".rag-meta.json");
            if (!Files.exists(metaFile)) return null;

            JsonNode node = mapper.readTree(metaFile.toFile());
            return node.path("lastIndexedCommit").asText(null);

        } catch (Exception e) {
            log.error("Failed to load commit for {}: {}", repoName, e.getMessage());
            return null;
        }
    }
}
