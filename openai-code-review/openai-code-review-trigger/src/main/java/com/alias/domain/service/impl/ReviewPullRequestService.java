package com.alias.domain.service.impl;

import com.alias.domain.model.ModelEnum;
import com.alias.domain.service.AbstractOpenAiCodeReviewService;
import com.alias.infrastructure.git.GitCommand;
import com.alias.infrastructure.openai.IOpenAI;
import com.alias.infrastructure.openai.dto.ChatCompletionRequestDTO;
import com.alias.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;
import com.alias.utils.VCSUtils;
import com.alias.utils.GitHubPrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alias.config.AppConfig;
import com.alias.domain.prompt.ReviewPrompts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alias.utils.ReviewJsonUtils;
import com.alias.utils.ReviewCommentUtils;
import com.alias.utils.IoUtils;
import com.alias.utils.SeverityUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ReviewPullRequestService extends AbstractOpenAiCodeReviewService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewPullRequestService.class);

    // PR 相关配置由调用方设置，不从环境变量读取
    private String repository; // owner/repo
    private String prNumber;   // 数字字符串
    private String prUrl;      // PR URL
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

    public void setPrUrl(String prUrl) {
        this.prUrl = prUrl;
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
     * 重载的 exec 方法，接收 PR URL 作为参数
     * 自动解析 URL 并设置相关参数，然后执行代码审查
     *
     * @param prUrl GitHub PR URL，格式：https://github.com/{owner}/{repo}/pull/{number}
     */
    public void exec(String prUrl) {
        logger.info("Starting PR review. prUrl={}", prUrl);
        GitHubPrUtils.PrInfo info = GitHubPrUtils.parsePrUrl(prUrl);
        logger.info("Parsed PR URL. repository={}, prNumber={}", info.repository, info.prNumber);
        this.setRepository(info.repository);
        this.setPrNumber(info.prNumber);
        this.setPrUrl(prUrl);
        logger.info("Executing review for {}/pull/{}", info.repository, info.prNumber);
        this.exec();
    }

    @Override
    protected String getDiffCode() throws IOException, InterruptedException {
        if (this.prUrl == null || this.prUrl.isEmpty()) {
            logger.error("PR URL is empty");
            throw new RuntimeException("PR URL is empty; please set via exec(prUrl)");
        }

        // 直接使用 GitHub API 获取 PR diff
        logger.info("Fetching PR diff from GitHub API. prUrl={}", this.prUrl);
        String diff = gitCommand.getPrDiff(this.prUrl);
        logger.info("Generated PR diff. size={} bytes", diff != null ? diff.length() : 0);
        return diff;
    }

    @Override
    protected String codeReview(String diffCode) throws Exception {
        logger.info("Submitting diff to LLM for review. model={}, diffSize={}", this.model != null ? this.model : ModelEnum.GPT_4O.getCode(), diffCode != null ? diffCode.length() : 0);
        final int MAX_PROMPT_CHARS = 180_000; // 粗略上限，避免超出供应商限制
        String safeDiff = diffCode == null ? "" : diffCode;
        ObjectMapper mapper = new ObjectMapper();

        // 使用 VCSUtils 将 diff 解析为结构化对象
        List<VCSUtils.FileChanges> files;
        try {
            files = VCSUtils.parseUnifiedDiff(safeDiff);
        } catch (Exception e) {
            logger.warn("Failed to parse unified diff; fallback to raw diff. err={}", e.toString());
            files = new ArrayList<>(); // 空列表占位，避免 null
        }

        if (files.isEmpty()) {
            logger.warn("No files found in diff, returning empty review");
            return mapper.writeValueAsString(createEmptyReview());
        }

        // 遍历每个文件，分别进行review
        List<JsonNode> fileReviews = new ArrayList<>();
        int totalScore = 0;
        int validScoreCount = 0;
        List<String> summaries = new ArrayList<>();
        List<JsonNode> allComments = new ArrayList<>();

        logger.info("Starting per-file review. totalFiles={}", files.size());
        for (int i = 0; i < files.size(); i++) {
            VCSUtils.FileChanges file = files.get(i);
            logger.info("Reviewing file {}/{}. path={}", i + 1, files.size(), file.path);

            try {
                // 从 RAG 获取 context（使用原始diff文本，因为RAG需要提取代码内容进行检索）
                String ragContext = getRagContext(safeDiff);

                // 对单个文件进行review
                String fileReviewJson = reviewSingleFile(file, ragContext, MAX_PROMPT_CHARS);

                // 解析单个文件的review结果
                JsonNode fileReview;
                try {
                    fileReview = mapper.readTree(fileReviewJson);
                } catch (Exception parseErr) {
                    logger.warn("Failed to parse file review JSON, attempting to extract. file={}, err={}", file.path, parseErr.toString());
                    String cleaned = ReviewJsonUtils.extractJsonPayload(fileReviewJson);
                    fileReview = mapper.readTree(cleaned);
                }

                fileReviews.add(fileReview);

                // 提取score
                Integer score = ReviewJsonUtils.safeInt(fileReview, "overall_score");
                if (score != null) {
                    totalScore += score;
                    validScoreCount++;
                }

                // 提取summary
                String summary = ReviewJsonUtils.safeText(fileReview, "summary");
                if (summary != null && !summary.isEmpty()) {
                    summaries.add(String.format("[%s] %s", file.path, summary));
                }

                // 提取comments
                JsonNode comments = fileReview.get("comments");
                if (comments != null && comments.isArray()) {
                    Iterator<JsonNode> it = comments.elements();
                    while (it.hasNext()) {
                        allComments.add(it.next());
                    }
                }

                logger.info("Completed review for file {}/{}. path={}, score={}", i + 1, files.size(), file.path, score);
            } catch (Exception e) {
                logger.error("Failed to review file. path={}, err={}", file.path, e.toString(), e);
                // 继续处理下一个文件，不中断整个流程
            }
        }

        // 整合所有文件的review结果
        int overallScore = validScoreCount > 0 ? totalScore / validScoreCount : 0;
        String combinedSummary = summaries.isEmpty() ? "No summary available." : String.join("\n\n", summaries);

        // 将 JsonNode 列表转换为 Map 列表，确保正确序列化
        List<Map<String, Object>> commentsList = new ArrayList<>();
        for (JsonNode comment : allComments) {
            Map<String, Object> commentMap = mapper.convertValue(comment, Map.class);
            commentsList.add(commentMap);
        }

        // 构建最终的整合结果
        Map<String, Object> mergedReview = new HashMap<>();
        mergedReview.put("overall_score", overallScore);
        mergedReview.put("summary", combinedSummary);
        mergedReview.put("general_review", "This review was generated by reviewing each file separately and merging the results.");
        mergedReview.put("comments", commentsList);

        String finalResult = mapper.writeValueAsString(mergedReview);
        logger.info("Completed per-file review. totalFiles={}, overallScore={}, totalComments={}", files.size(), overallScore, allComments.size());
        return finalResult;
    }

    /**
     * 对单个文件进行review
     *
     * @param file           文件变更对象
     * @param ragContext     RAG上下文
     * @param maxPromptChars 最大prompt字符数限制
     * @return review结果的JSON字符串
     * @throws Exception 如果review失败
     */
    private String reviewSingleFile(VCSUtils.FileChanges file, String ragContext, int maxPromptChars) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // 将单个文件转换为JSON
        List<VCSUtils.FileChanges> singleFileList = new ArrayList<>();
        singleFileList.add(file);
        String structuredJson = mapper.writeValueAsString(singleFileList);

        String basePrompt = ReviewPrompts.PR_REVIEW_PROMPT;
        // 将占位符替换为结构化 JSON 和 RAG context
        String mergedPrompt = basePrompt.replace("<Git diff>", structuredJson).replace("<RAG context>", ragContext != null && !ragContext.isEmpty() ? ragContext : "No additional context available.");

        if (mergedPrompt.length() > maxPromptChars) {
            logger.warn("Prompt too large for single file. file={}, promptSize={}, maxSize={}", file.path, mergedPrompt.length(), maxPromptChars);
            throw new RuntimeException("Prompt too large for file: " + file.path);
        }

        ChatCompletionRequestDTO chatCompletionRequest = new ChatCompletionRequestDTO();
        chatCompletionRequest.setModel(this.model != null ? this.model : ModelEnum.GPT_4O.getCode());
        chatCompletionRequest.setMessages(new ArrayList<ChatCompletionRequestDTO.Prompt>() {
            private static final long serialVersionUID = -7988151926241837899L;
            {
                add(new ChatCompletionRequestDTO.Prompt("user", mergedPrompt));
            }
        });

        logger.debug("Request for file: {}", file.path);
        ChatCompletionSyncResponseDTO completions = openAI.completions(chatCompletionRequest);
        ChatCompletionSyncResponseDTO.Message message = completions.getChoices().get(0).getMessage();
        logger.debug("Review response for file: {}, contentSize={}", file.path, message != null && message.getContent() != null ? message.getContent().length() : 0);

        return message.getContent();
    }

    /**
     * 创建空的review结果
     */
    private Map<String, Object> createEmptyReview() {
        Map<String, Object> review = new HashMap<>();
        review.put("overall_score", 0);
        review.put("summary", "No changes found in diff.");
        review.put("general_review", "");
        review.put("comments", new ArrayList<>());
        return review;
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
            String cleaned = ReviewJsonUtils.extractJsonPayload(recommend);
            root = mapper.readTree(cleaned);
        }
        Integer overallScore = ReviewJsonUtils.safeInt(root, "overall_score");
        String summary = ReviewJsonUtils.safeText(root, "summary");
        String general = ReviewJsonUtils.safeText(root, "general_review");
        StringBuilder topBuilder = new StringBuilder();
        if (overallScore != null) {
            topBuilder.append("### 😀 Overall Score\n").append("⭐️ ").append(overallScore).append("/100").append("\n\n");
        }
        topBuilder.append(ReviewCommentUtils.buildTopLevelComment(summary, general));
        String combinedTop = topBuilder.toString();
        postCommentToGithubPr(combinedTop);

        // Inline comments
        JsonNode comments = root.get("comments");
        if (comments != null && comments.isArray() && comments.size() > 0) {
            // 通过 GitHub API 获取 PR head commit SHA
            String commitSha = gitCommand.getPrHeadCommitSha(this.repository, this.prNumber);
            List<RankedReviewComment> rankedComments = new ArrayList<>();
            Iterator<JsonNode> it = comments.elements();
            int seq = 0;
            while (it.hasNext()) {
                JsonNode c = it.next();
                String path = ReviewJsonUtils.safeText(c, "path");
                Integer line = ReviewJsonUtils.safeInt(c, "line");
                String severity = ReviewJsonUtils.safeText(c, "severity");
                String body = ReviewJsonUtils.safeText(c, "body");
                String suggestion = ReviewJsonUtils.safeText(c, "suggestion");
                if (path == null || line == null || line <= 0 || body == null || body.isEmpty()) {
                    continue;
                }
                String fullBody = body;
                if (severity != null && !severity.isEmpty()) {
                    String sevEmoji;
                    String sevLower = severity.toLowerCase();
                    if ("critical".equals(sevLower)) {
                        sevEmoji = "🛑";
                    } else if ("major".equals(sevLower)) {
                        sevEmoji = "⚠️";
                    } else if ("minor".equals(sevLower)) {
                        sevEmoji = "ℹ️";
                    } else if ("suggestion".equals(sevLower)) {
                        sevEmoji = "💡";
                    } else {
                        sevEmoji = "🔎";
                    }
                    fullBody = "🔎 **Severity:** " + sevEmoji + " " + severity + "\n\n" + fullBody;
                }
                if (suggestion != null && !suggestion.isEmpty()) {
                    fullBody = fullBody + "\n\n" + suggestion + "\n";
                }
                int rank = SeverityUtils.severityRank(severity);
                rankedComments.add(new RankedReviewComment(new ReviewComment(path, "RIGHT", line, fullBody), rank, seq++));
            }
            if (!rankedComments.isEmpty()) {
                rankedComments.sort((a, b) -> {
                    if (a.rank != b.rank) return Integer.compare(a.rank, b.rank);
                    return Integer.compare(a.index, b.index);
                });
                List<ReviewComment> ordered = new ArrayList<>();
                for (RankedReviewComment rc : rankedComments) {
                    ordered.add(rc.comment);
                }
                createPullRequestReview(commitSha, "AI Code Review inline comments", ordered);
            }
        }
        return prUrl;
    }

    @Override
    protected void pushMessage(String logUrl) throws Exception {
        // TODO: not implemented
    }

    // Removed file-based prompt loader; prompt is provided by ReviewPrompts class.

    /**
     * 从 RAG 服务获取代码上下文
     *
     * @param code 代码内容（原始diff文本）
     * @return RAG context 字符串
     * @throws Exception 如果调用RAG接口失败
     */
    private String getRagContext(String code) throws Exception {
        if (this.repository == null || this.repository.isEmpty()) {
            logger.warn("Repository is empty, cannot get RAG context");
            return "";
        }

        // 从 repository 中提取 repoName（格式：owner/repo，提取 repo 部分）
        String repoName = extractRepoName(this.repository);
        if (repoName == null || repoName.isEmpty()) {
            logger.warn("Cannot extract repoName from repository: {}", this.repository);
            return "";
        }

        // 获取 RAG 服务 URL
        String ragBaseUrl = AppConfig.getInstance().getString("rag", "apiBaseUrl");
        if (ragBaseUrl == null || ragBaseUrl.isEmpty()) {
            logger.warn("RAG API base URL is not configured");
            return "";
        }

        // 构建 RAG 接口 URL
        String apiUrl = ragBaseUrl + "/review-context";

        logger.info("Calling RAG API to get context. repoName={}, codeSize={}", repoName, code != null ? code.length() : 0);

        // 构建请求体 JSON
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("repoName", repoName);
        requestMap.put("code", code != null ? code : "");
        String requestBody = mapper.writeValueAsString(requestMap);

        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000); // 10 seconds
        conn.setReadTimeout(30000); // 30 seconds

        // 发送请求体
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int httpCode = conn.getResponseCode();
        if (httpCode / 100 != 2) {
            String errMsg = IoUtils.readStreamSafely(conn.getErrorStream());
            throw new RuntimeException("RAG API call failed, code=" + httpCode + ", err=" + errMsg);
        }

        // 解析响应
        String responseBody = IoUtils.readStreamSafely(conn.getInputStream());
        JsonNode root = mapper.readTree(responseBody);

        // 检查响应code
        String responseCode = ReviewJsonUtils.safeText(root, "code");
        if (!"0000".equals(responseCode)) {
            String info = ReviewJsonUtils.safeText(root, "info");
            logger.warn("RAG API returned non-success code: {}, info: {}", responseCode, info);
            return "";
        }

        // 提取data字段
        String context = ReviewJsonUtils.safeText(root, "data");
        if (context == null || context.isEmpty()) {
            logger.warn("RAG API returned empty context");
            return "";
        }

        logger.info("Successfully retrieved RAG context. contextSize={}", context.length());
        return context;
    }

    /**
     * 从 repository 字符串中提取 repoName
     * 格式：owner/repo，返回 repo 部分
     *
     * @param repository repository 字符串，格式：owner/repo
     * @return repoName
     */
    private String extractRepoName(String repository) {
        if (repository == null || repository.isEmpty()) {
            return null;
        }
        int lastSlash = repository.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < repository.length() - 1) {
            return repository.substring(lastSlash + 1);
        }
        return repository;
    }

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
        String payload = "{\"body\":" + ReviewJsonUtils.toJsonString(body) + "}";

        logger.info("Posting comment to GitHub. api={}, repo={}, pr={}", api, repo, this.prNumber);
        URL url = new URL(api);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "alias-openai-code-review");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            String errMsg = IoUtils.readStreamSafely(conn.getErrorStream());
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
        sb.append("\"commit_id\":").append(ReviewJsonUtils.toJsonString(commitSha)).append(",");
        sb.append("\"body\":").append(ReviewJsonUtils.toJsonString(body)).append(",");
        sb.append("\"event\":\"COMMENT\",");
        sb.append("\"comments\":[");
        for (int i = 0; i < comments.size(); i++) {
            ReviewComment c = comments.get(i);
            sb.append("{").append("\"path\":").append(ReviewJsonUtils.toJsonString(c.path)).append(",").append("\"side\":").append(ReviewJsonUtils.toJsonString(c.side)).append(",").append("\"line\":").append(c.line).append(",").append("\"body\":").append(ReviewJsonUtils.toJsonString(c.body)).append("}");
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
        conn.setRequestProperty("User-Agent", "alias-openai-code-review");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            String errMsg = IoUtils.readStreamSafely(conn.getErrorStream());
            throw new RuntimeException("Create PR review failed, code=" + code + ", err=" + errMsg);
        }
        logger.info("PR review created successfully. code={}", code);
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


    private static final class RankedReviewComment {
        final ReviewComment comment;
        final int rank;
        final int index;

        RankedReviewComment(ReviewComment comment, int rank, int index) {
            this.comment = comment;
            this.rank = rank;
            this.index = index;
        }
    }
}

