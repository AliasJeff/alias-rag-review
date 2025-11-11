package com.alias.middleware.sdk.domain.service.impl;

import com.alias.middleware.sdk.domain.model.ModelEnum;
import com.alias.middleware.sdk.domain.service.AbstractOpenAiCodeReviewService;
import com.alias.middleware.sdk.infrastructure.git.GitCommand;
import com.alias.middleware.sdk.infrastructure.openai.IOpenAI;
import com.alias.middleware.sdk.infrastructure.openai.dto.ChatCompletionRequestDTO;
import com.alias.middleware.sdk.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;
import com.alias.middleware.sdk.types.utils.GitHubPrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alias.middleware.sdk.config.AppConfig;
import com.alias.middleware.sdk.domain.prompt.ReviewPrompts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ReviewPullRequestService extends AbstractOpenAiCodeReviewService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewPullRequestService.class);

    // PR 相关配置由调用方设置，不从环境变量读取
    private String repository; // owner/repo
    private String prNumber;   // 数字字符串
    private String baseRef;    // base 分支引用
    private String headRef;    // head 分支引用
    private String model;      // 模型名称，默认使用 GPT-4o

    public ReviewPullRequestService(GitCommand gitCommand, IOpenAI openAI) {
        super(gitCommand, openAI);
        this.model = ModelEnum.GPT_4O.getCode(); // 默认模型
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public void setPrNumber(String prNumber) {
        this.prNumber = prNumber;
    }

    public void setBaseRef(String baseRef) {
        this.baseRef = baseRef;
    }

    public void setHeadRef(String headRef) {
        this.headRef = headRef;
    }

    /**
     * 设置使用的模型
     *
     * @param model 模型名称，例如 ModelEnum.GPT_4O.getCode() 或 ModelEnum.GLM_4_FLASH.getCode()
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * 设置使用的模型
     *
     * @param modelEnum 模型枚举
     */
    public void setModel(ModelEnum modelEnum) {
        this.model = modelEnum.getCode();
    }

    /**
     * 重载的 exec 方法，接收 PR URL、baseRef 和 headRef 作为参数
     * 自动解析 URL 并设置相关参数，然后执行代码审查
     *
     * @param prUrl GitHub PR URL，格式：https://github.com/{owner}/{repo}/pull/{number}
     * @param baseRef base 分支引用
     * @param headRef head 分支引用
     */
    public void exec(String prUrl, String baseRef, String headRef) {
        logger.info("Starting PR review. prUrl={}, baseRef={}, headRef={}", prUrl, baseRef, headRef);
        GitHubPrUtils.PrInfo info = GitHubPrUtils.parsePrUrl(prUrl);
        logger.info("Parsed PR URL. repository={}, prNumber={}", info.repository, info.prNumber);
        this.setRepository(info.repository);
        this.setPrNumber(info.prNumber);
        this.setBaseRef(baseRef);
        this.setHeadRef(headRef);
        logger.info("Executing review for {}/pull/{} with base={} head={}", info.repository, info.prNumber, baseRef, headRef);
        this.exec();
    }

    @Override
    protected String getDiffCode() throws IOException, InterruptedException {
        String base = this.baseRef;
        String head = this.headRef;

        if (base == null || base.isEmpty() || head == null || head.isEmpty()) {
            logger.error("Base or head ref is empty. base='{}', head='{}'", base, head);
            throw new RuntimeException("baseRef or headRef is empty; please set via exec(url, baseRef, headRef)");
        }

        // 确保本地有最新远端引用
        logger.info("Fetching refs from origin. base={}, head={}", base, head);
        gitCommand.fetchRemoteRef("origin", base);
        gitCommand.fetchRemoteRef("origin", head);

        // 使用三点语法获取 merge-base 到 head 的变更
        String diff = gitCommand.diffRemoteRefsThreeDot("origin", base, head);
        logger.info("Generated git diff between origin/{}...origin/{}. size={} bytes", base, head, diff != null ? diff.length() : 0);
        return diff;
    }

    @Override
    protected String codeReview(String diffCode) throws Exception {
        logger.info("Submitting diff to LLM for review. model={}, diffSize={}", this.model != null ? this.model : ModelEnum.GPT_4O.getCode(), diffCode != null ? diffCode.length() : 0);
        ChatCompletionRequestDTO chatCompletionRequest = new ChatCompletionRequestDTO();
        chatCompletionRequest.setModel(this.model != null ? this.model : ModelEnum.GPT_4O.getCode());
        chatCompletionRequest.setMessages(new ArrayList<ChatCompletionRequestDTO.Prompt>() {
            private static final long serialVersionUID = -7988151926241837899L;
            {
                String mergedPrompt = ReviewPrompts.PR_REVIEW_COPILOT_STYLE_SCORE_100
                        .replace("<Git diff>", diffCode == null ? "" : diffCode);
                add(new ChatCompletionRequestDTO.Prompt("user", mergedPrompt));
            }
        });

        ChatCompletionSyncResponseDTO completions = openAI.completions(chatCompletionRequest);
        ChatCompletionSyncResponseDTO.Message message = completions.getChoices().get(0).getMessage();
        logger.info("Received review response from LLM. contentSize={}", message != null && message.getContent() != null ? message.getContent().length() : 0);
        logger.debug("Review response: {}", message.getContent());
        return message.getContent();
    }

    @Override
    protected String recordCodeReview(String recommend) throws Exception {
        logger.info("Posting review to GitHub PR. repository={}, prNumber={}", this.repository, this.prNumber);
        // Expect LLM to return JSON content as specified by prompt. Attempt to parse.
        ObjectMapper mapper = new ObjectMapper();
        String prUrl = "https://github.com/" + this.repository + "/pull/" + this.prNumber;
        JsonNode root;
        try {
            root = mapper.readTree(recommend);
        } catch (Exception parseErr) {
            logger.warn("LLM output is not pure JSON, attempting to extract JSON. err={}", parseErr.toString());
            String cleaned = extractJsonPayload(recommend);
            root = mapper.readTree(cleaned);
        }
        Integer overallScore = safeInt(root, "overall_score");
        String summary = safeText(root, "summary");
        String general = safeText(root, "general_review");
        StringBuilder topBuilder = new StringBuilder();
        if (overallScore != null) {
            topBuilder.append("### 😀 整体评分\n").append("⭐️ ").append(overallScore).append("/100").append("\n\n");
        }
        topBuilder.append(buildTopLevelComment(summary, general));
        String combinedTop = topBuilder.toString();
        postCommentToGithubPr(combinedTop);

        // Inline comments
        JsonNode comments = root.get("comments");
        if (comments != null && comments.isArray() && comments.size() > 0) {
            // Ensure refs are fetched and resolve commit sha of head
            gitCommand.fetchRemoteRef("origin", this.headRef);
            String commitSha = gitCommand.getRemoteRefCommitSha("origin", this.headRef);
            List<ReviewComment> reviewComments = new ArrayList<>();
            Iterator<JsonNode> it = comments.elements();
            while (it.hasNext()) {
                JsonNode c = it.next();
                String path = safeText(c, "path");
                Integer line = safeInt(c, "line");
                String confidence = safeText(c, "confidence");
                String body = safeText(c, "body");
                String suggestion = safeText(c, "suggestion");
                if (path == null || line == null || line <= 0 || body == null || body.isEmpty()) {
                    continue;
                }
                String fullBody = body;
                if (confidence != null && !confidence.isEmpty()) {
                    String confEmoji;
                    if ("high".equalsIgnoreCase(confidence) || "high confidence".equalsIgnoreCase(confidence)) {
                        confEmoji = "🔥";
                    } else if ("low".equalsIgnoreCase(confidence) || "low confidence".equalsIgnoreCase(confidence)) {
                        confEmoji = "⚠️";
                    } else {
                        confEmoji = "💡";
                    }
                    fullBody = "🔎 **Confidence:** " + confEmoji + " " + confidence + "\n\n" + fullBody;
                }
                if (suggestion != null && !suggestion.isEmpty()) {
                    fullBody = fullBody + "\n\n```suggestion\n" + suggestion + "\n```";
                }
                reviewComments.add(new ReviewComment(path, "RIGHT", line, fullBody));
            }
            if (!reviewComments.isEmpty()) {
                createPullRequestReview(commitSha, "AI Code Review inline comments", reviewComments);
            }
        }
        return prUrl;
    }

    @Override
    protected void pushMessage(String logUrl) throws Exception {
        // TODO: not implemented
    }

    // Removed file-based prompt loader; prompt is provided by ReviewPrompts class.

    private String postCommentToGithubPr(String body) throws Exception {
        String repo = this.repository;
        String token = AppConfig.getInstance().requireString("github", "token");
        if (repo == null || repo.isEmpty()) {
            throw new RuntimeException("GITHUB_REPOSITORY is empty");
        }
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("GITHUB_TOKEN is empty");
        }

        if (this.prNumber == null || this.prNumber.isEmpty()) {
            throw new RuntimeException("GITHUB_PR_NUMBER is empty and cannot be inferred from GITHUB_EVENT_PATH");
        }

        String api = "https://api.github.com/repos/" + repo + "/issues/" + this.prNumber + "/comments";
        String payload = "{\"body\":" + toJsonString(body) + "}";

        logger.info("Posting comment to GitHub. api={}, repo={}, pr={}", api, repo, this.prNumber);
        URL url = new URL(api);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "alias-openai-code-review-sdk");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            String errMsg = readStreamSafely(conn.getErrorStream());
            throw new RuntimeException("GitHub comment failed, code=" + code + ", err=" + errMsg);
        }
        // 返回 PR 链接，便于日志打印
        logger.info("Comment posted to GitHub PR successfully. code={}, url=https://github.com/{}/pull/{}", code, repo, this.prNumber);
        return "https://github.com/" + repo + "/pull/" + this.prNumber;
    }

    private void createPullRequestReview(String commitSha, String body, List<ReviewComment> comments) throws Exception {
        String repo = this.repository;
        String token = AppConfig.getInstance().requireString("github", "token");
        if (repo == null || repo.isEmpty()) {
            throw new RuntimeException("GITHUB_REPOSITORY is empty");
        }
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("GITHUB_TOKEN is empty");
        }
        if (this.prNumber == null || this.prNumber.isEmpty()) {
            throw new RuntimeException("GITHUB_PR_NUMBER is empty");
        }
        String api = "https://api.github.com/repos/" + repo + "/pulls/" + this.prNumber + "/reviews";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"commit_id\":").append(toJsonString(commitSha)).append(",");
        sb.append("\"body\":").append(toJsonString(body)).append(",");
        sb.append("\"event\":\"COMMENT\",");
        sb.append("\"comments\":[");
        for (int i = 0; i < comments.size(); i++) {
            ReviewComment c = comments.get(i);
            sb.append("{")
                    .append("\"path\":").append(toJsonString(c.path)).append(",")
                    .append("\"side\":").append(toJsonString(c.side)).append(",")
                    .append("\"line\":").append(c.line).append(",")
                    .append("\"body\":").append(toJsonString(c.body))
                    .append("}");
            if (i < comments.size() - 1) sb.append(",");
        }
        sb.append("]}");
        String payload = sb.toString();

        logger.info("Creating PR review with {} comments. api={}", comments.size(), api);
        URL url = new URL(api);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "alias-openai-code-review-sdk");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            String errMsg = readStreamSafely(conn.getErrorStream());
            throw new RuntimeException("Create PR review failed, code=" + code + ", err=" + errMsg);
        }
        logger.info("PR review created successfully. code={}", code);
    }

    private String buildTopLevelComment(String summary, String generalReview) {
        StringBuilder sb = new StringBuilder();
        if (summary != null && !summary.isEmpty()) {
            sb.append("### PR 变更摘要\n").append(summary).append("\n\n");
        }
        if (generalReview != null && !generalReview.isEmpty()) {
            sb.append("### 综合审查\n").append(generalReview);
        }
        return sb.length() == 0 ? "AI 代码审查无可用摘要或综合意见。" : sb.toString();
    }

    private static String safeText(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        return (s != null && !s.trim().isEmpty()) ? s : null;
    }

    private static Integer safeInt(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        try {
            return n.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class ReviewComment {
        final String path;
        final String side; // "RIGHT" or "LEFT"
        final int line;
        final String body;
        ReviewComment(String path, String side, int line, String body) {
            this.path = path;
            this.side = side;
            this.line = line;
            this.body = body;
        }
    }

    private String readStreamSafely(java.io.InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private String toJsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
    
    /**
     * Extract a JSON object string from a potentially markdown-formatted LLM output.
     * Handles ```json ... ``` fences and leading/trailing prose. Falls back to first {...} slice.
     */
    private String extractJsonPayload(String text) {
        if (text == null) return "";
        String s = text.trim();
        // Handle fenced code blocks ```json ... ```
        int fenceStart = s.indexOf("```");
        if (fenceStart >= 0) {
            int fenceEnd = s.indexOf("```", fenceStart + 3);
            if (fenceEnd > fenceStart) {
                String fenced = s.substring(fenceStart + 3, fenceEnd);
                // Remove optional language hint like "json" or "JSON"
                String trimmed = fenced.trim();
                if (trimmed.regionMatches(true, 0, "json", 0, Math.min(4, trimmed.length()))) {
                    trimmed = trimmed.substring(Math.min(4, trimmed.length())).trim();
                }
                return sliceFirstJsonObject(trimmed);
            }
        }
        // No fences; try to slice first JSON object
        return sliceFirstJsonObject(s);
    }
    
    private String sliceFirstJsonObject(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return s.substring(start, end + 1).trim();
        }
        // As a last resort, return original trimmed string
        return s.trim();
    }
}


