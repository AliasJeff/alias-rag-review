package com.alias.infrastructure.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitCommand {

    private final Logger logger = LoggerFactory.getLogger(GitCommand.class);

    private final String githubToken;

    public GitCommand(String githubToken) {
        this.githubToken = githubToken;
    }

    public String getPrDiff(String prUrl) throws IOException {
//        // 直接请求 GitHub API 的 .diff 接口
//        if (!prUrl.endsWith(".diff")) {
//            if (prUrl.endsWith("/")) prUrl = prUrl.substring(0, prUrl.length() - 1);
//            prUrl = prUrl + ".diff";
//        }
//
//        URL url = new URL(prUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestProperty("Authorization", "token " + githubToken);
//        conn.setRequestProperty("Accept", "application/vnd.github.v3.diff");
//
//        int status = conn.getResponseCode();
//        if (status != 200) {
//            throw new IOException("GitHub API request failed: " + status + " " + conn.getResponseMessage());
//        }
//
//        try (InputStream in = conn.getInputStream()) {
//            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
//        }
        // 解析 owner / repo / number
        Pattern pattern = Pattern.compile("github\\.com/([^/]+)/([^/]+)/pull/(\\d+)");
        Matcher m = pattern.matcher(prUrl);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid PR URL: " + prUrl);
        }

        String owner = m.group(1);
        String repo = m.group(2);
        String number = m.group(3);

        String apiUrl = String.format(
                "https://api.github.com/repos/%s/%s/pulls/%s", owner, repo, number
        );

        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "token " + githubToken);
        conn.setRequestProperty("Accept", "application/vnd.github.v3.diff");

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("GitHub API request failed: " + status + " " + conn.getResponseMessage());
        }

        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Get the head commit SHA for a PR via GitHub API.
     *
     * @param repository Repository in format "owner/repo"
     * @param prNumber   PR number as string
     * @return The head commit SHA of the PR
     */
    public String getPrHeadCommitSha(String repository, String prNumber) throws IOException {
        String api = "https://api.github.com/repos/" + repository + "/pulls/" + prNumber;
        logger.info("Fetching PR head commit SHA. api={}", api);
        URL url = new URL(api);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "token " + githubToken);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "alias-openai-code-review");

        int status = conn.getResponseCode();
        if (status != 200) {
            String errMsg = readStreamSafely(conn.getErrorStream());
            throw new IOException("GitHub API request failed: " + status + " " + conn.getResponseMessage() + ", err=" + errMsg);
        }

        try (InputStream in = conn.getInputStream()) {
            String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            JsonNode head = root.get("head");
            if (head == null) {
                throw new IOException("Cannot find 'head' field in PR response");
            }
            JsonNode shaNode = head.get("sha");
            if (shaNode == null || shaNode.isNull()) {
                throw new IOException("Cannot find 'sha' field in PR head");
            }
            String sha = shaNode.asText();
            logger.info("Resolved PR head commit SHA: {}", sha);
            return sha;
        }
    }

    private String readStreamSafely(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Failed to read error stream: " + e.getMessage();
        }
    }
}
