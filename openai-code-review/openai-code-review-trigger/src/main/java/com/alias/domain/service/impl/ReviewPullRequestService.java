package com.alias.domain.service.impl;

import com.alias.config.AppConfig;
import com.alias.domain.model.ModelEnum;
import com.alias.domain.model.PrSnapshot;
import com.alias.domain.prompt.ReviewPrompts;
import com.alias.domain.service.AbstractOpenAiCodeReviewService;
import com.alias.domain.service.IPrSnapshotService;
import com.alias.domain.utils.ChatUtils;
import com.alias.infrastructure.git.GitCommand;
import com.alias.utils.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ReviewPullRequestService extends AbstractOpenAiCodeReviewService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewPullRequestService.class);

    // PR 相关配置由调用方设置，不从环境变量读取
    private String repository; // owner/repo
    private String prNumber;   // 数字字符串
    private String prUrl;      // PR URL
    private String model;      // 模型名称，默认使用 GPT-4o

    private ChatClient chatClient;
    private final IPrSnapshotService prSnapshotService;
    private UUID clientIdentifier;

    public ReviewPullRequestService(GitCommand gitCommand, ChatClient chatClient) {
        this(gitCommand, chatClient, null);
    }

    public ReviewPullRequestService(GitCommand gitCommand, ChatClient chatClient, IPrSnapshotService prSnapshotService) {
        super(gitCommand, null);
        this.chatClient = chatClient;
        this.prSnapshotService = prSnapshotService;
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

    public void setClientIdentifier(UUID clientIdentifier) {
        this.clientIdentifier = clientIdentifier;
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

        persistSnapshotAsync(files);

        // 获取 RAG context
        String ragContext = getRagContext(safeDiff);

        // 步骤1: 先进行整体PR摘要（一次性发送所有文件）
        logger.info("Starting PR overall summary. totalFiles={}", files.size());
        JsonNode prSummaryJson = null;
        try {
            String prSummaryResponse = generatePrSummary(files, ragContext, MAX_PROMPT_CHARS);
            try {
                prSummaryJson = mapper.readTree(prSummaryResponse);
            } catch (Exception parseErr) {
                logger.warn("Failed to parse PR summary JSON, attempting to extract. err={}", parseErr.toString());
                String cleaned = ReviewJsonUtils.extractJsonPayload(prSummaryResponse);
                prSummaryJson = mapper.readTree(cleaned);
            }
            logger.info("PR summary generated successfully");
        } catch (Exception e) {
            logger.error("Failed to generate PR summary, continuing with per-file review. err={}", e.toString(), e);
        }

        // 步骤2: 遍历每个文件，分别进行review
        List<JsonNode> fileReviews = new ArrayList<>();
        List<JsonNode> allComments = new ArrayList<>();

        logger.info("Starting per-file review. totalFiles={}", files.size());
        for (int i = 0; i < files.size(); i++) {
            VCSUtils.FileChanges file = files.get(i);
            logger.info("Reviewing file {}/{}. path={}", i + 1, files.size(), file.path);

            try {
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

                // 提取comments
                JsonNode comments = fileReview.get("comments");
                if (comments != null && comments.isArray()) {
                    Iterator<JsonNode> it = comments.elements();
                    while (it.hasNext()) {
                        allComments.add(it.next());
                    }
                }

                logger.info("Completed review for file {}/{}. path={}, comments={}", i + 1, files.size(), file.path, comments != null && comments.isArray() ? comments.size() : 0);
            } catch (Exception e) {
                logger.error("Failed to review file. path={}, err={}", file.path, e.toString(), e);
                // 继续处理下一个文件，不中断整个流程
            }
        }

        // 将 JsonNode 列表转换为 Map 列表，确保正确序列化
        List<Map<String, Object>> commentsList = new ArrayList<>();
        for (JsonNode comment : allComments) {
            Map<String, Object> commentMap = mapper.convertValue(comment, new TypeReference<Map<String, Object>>() {
            });
            commentsList.add(commentMap);
        }

        // 构建最终的整合结果，包含PR摘要
        Map<String, Object> mergedReview = new HashMap<>();
        mergedReview.put("comments", commentsList);

        // 如果PR摘要存在，将其添加到结果中
        if (prSummaryJson != null) {
            JsonNode prSummary = prSummaryJson.get("pr_summary");
            if (prSummary != null) {
                // 更新review_summary中的total_comments
                JsonNode reviewSummary = prSummary.get("review_summary");
                if (reviewSummary != null) {
                    Map<String, Object> reviewSummaryMap = mapper.convertValue(reviewSummary, new TypeReference<Map<String, Object>>() {
                    });
                    reviewSummaryMap.put("total_comments", allComments.size());
                    Map<String, Object> prSummaryMap = mapper.convertValue(prSummary, new TypeReference<Map<String, Object>>() {
                    });
                    prSummaryMap.put("review_summary", reviewSummaryMap);
                    mergedReview.put("pr_summary", prSummaryMap);
                } else {
                    mergedReview.put("pr_summary", mapper.convertValue(prSummary, new TypeReference<Map<String, Object>>() {
                    }));
                }
            }
        }

        String finalResult = mapper.writeValueAsString(mergedReview);
        logger.info("Completed per-file review. totalFiles={}, totalComments={}", files.size(), allComments.size());
        return finalResult;
    }

    private void persistSnapshotAsync(List<VCSUtils.FileChanges> files) {
        if (prSnapshotService == null || files == null || files.isEmpty() || prUrl == null || prUrl.isEmpty()) {
            logger.debug("Skip snapshot persistence due to missing dependency or data. url={}", prUrl);
            return;
        }

        List<VCSUtils.FileChanges> snapshotFiles = new ArrayList<>(files);
        String snapshotUrl = this.prUrl;
        String snapshotRepo = this.repository;
        Integer snapshotPrNumber = safeParsePrNumber(this.prNumber);
        UUID snapshotClient = this.clientIdentifier;

        CompletableFuture.runAsync(() -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> payload = new HashMap<>();
                List<Map<String, Object>> filePayload = mapper.convertValue(snapshotFiles, new TypeReference<List<Map<String, Object>>>() {
                });
                payload.put("files", filePayload);
                payload.put("totalFiles", snapshotFiles.size());

                PrSnapshot snapshot = PrSnapshot.builder().url(snapshotUrl).clientIdentifier(snapshotClient).repoName(snapshotRepo).prNumber(snapshotPrNumber).branch(null).fileChanges(payload).build();

                prSnapshotService.createSnapshot(snapshot);
                logger.info("Persisted PR snapshot asynchronously. url={}, files={}", snapshotUrl, snapshotFiles.size());
            } catch (Exception e) {
                logger.warn("Failed to persist PR snapshot. url={}, err={}", snapshotUrl, e.getMessage(), e);
            }
        });
    }

    private Integer safeParsePrNumber(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            logger.warn("Failed to parse PR number: {}", raw);
            return null;
        }
    }

    /**
     * 生成PR整体摘要
     *
     * @param files          所有文件变更对象列表
     * @param ragContext     RAG上下文
     * @param maxPromptChars 最大prompt字符数限制
     * @return PR摘要的JSON字符串
     * @throws Exception 如果生成失败
     */
    private String generatePrSummary(List<VCSUtils.FileChanges> files, String ragContext, int maxPromptChars) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // 将所有文件转换为JSON
        String structuredJson = mapper.writeValueAsString(files);

        String basePrompt = ReviewPrompts.PR_SUMMARY_PROMPT;
        // 将占位符替换为结构化 JSON 和 RAG context
        String mergedPrompt = basePrompt.replace("<Git diff>", structuredJson).replace("<RAG context>", ragContext != null && !ragContext.isEmpty() ? ragContext : "No additional context available.");

        if (mergedPrompt.length() > maxPromptChars) {
            logger.warn("Prompt too large for PR summary. promptSize={}, maxSize={}", mergedPrompt.length(), maxPromptChars);
        }

        logger.debug("Request for PR summary for {} files", files.size());

        // Build messages for ChatClient
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new UserMessage(mergedPrompt));

        // Create prompt with model options
        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder().model(this.model != null ? this.model : ModelEnum.GPT_4O.getCode()).build());

        // Call ChatClient
        org.springframework.ai.chat.model.ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        String content = response.getResult().getOutput().getText();

        logger.debug("PR summary response for {} files, contentSize={}", files.size(), content != null ? content.length() : 0);

        return content;
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
//            throw new RuntimeException("Prompt too large for file: " + file.path);
        }

        logger.debug("Request for file: {}", file.path);

        // Build messages for ChatClient
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new UserMessage(mergedPrompt));

        // Create prompt with model options
        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder().model(this.model != null ? this.model : ModelEnum.GPT_4O.getCode()).build());

        // Call ChatClient
        org.springframework.ai.chat.model.ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        String content = response.getResult().getOutput().getText();

        logger.debug("Review response for file: {}, contentSize={}", file.path, content != null ? content.length() : 0);

        return content;
    }

    /**
     * 创建空的review结果
     */
    private Map<String, Object> createEmptyReview() {
        Map<String, Object> review = new HashMap<>();
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
        // Build top-level comment from PR summary if available
        StringBuilder topBuilder = new StringBuilder();
        JsonNode prSummary = root.get("pr_summary");
        if (prSummary != null) {
            String title = ReviewJsonUtils.safeText(prSummary, "title");
            String description = ReviewJsonUtils.safeText(prSummary, "description");
            if (title != null && !title.isEmpty()) {
                topBuilder.append("### ").append(title).append("\n\n");
            }
            if (description != null && !description.isEmpty()) {
                topBuilder.append(description).append("\n\n");
            }

            JsonNode keyChanges = prSummary.get("key_changes");
            if (keyChanges != null && keyChanges.isArray() && keyChanges.size() > 0) {
                topBuilder.append("#### Key Changes\n\n");
                Iterator<JsonNode> keyChangeIterator = keyChanges.elements();
                while (keyChangeIterator.hasNext()) {
                    String change = keyChangeIterator.next().asText();
                    if (change != null && !change.isEmpty()) {
                        topBuilder.append("- ").append(change).append("\n");
                    }
                }
                topBuilder.append("\n");
            }

            JsonNode reviewSummary = prSummary.get("review_summary");
            if (reviewSummary != null && reviewSummary.isObject()) {
                Integer totalFilesReviewed = ReviewJsonUtils.safeInt(reviewSummary, "total_files_reviewed");
                Integer totalComments = ReviewJsonUtils.safeInt(reviewSummary, "total_comments");

                if (totalFilesReviewed != null || totalComments != null) {
                    topBuilder.append("#### Review Summary\n\n");
                    if (totalFilesReviewed != null) {
                        topBuilder.append("- Total Files Reviewed: ").append(totalFilesReviewed).append("\n");
                    }
                    if (totalComments != null) {
                        topBuilder.append("- Total Comments: ").append(totalComments).append("\n");
                    }
                    topBuilder.append("\n");
                }

                JsonNode filesReviewed = reviewSummary.get("files");
                if (filesReviewed != null && filesReviewed.isArray() && filesReviewed.size() > 0) {
                    topBuilder.append("#### Files Reviewed\n\n");
                    Iterator<JsonNode> filesIterator = filesReviewed.elements();
                    int idx = 1;
                    while (filesIterator.hasNext()) {
                        JsonNode fileSummary = filesIterator.next();
                        String filePath = ReviewJsonUtils.safeText(fileSummary, "file");
                        String fileDescription = ReviewJsonUtils.safeText(fileSummary, "description");
                        if (filePath == null && fileDescription == null) {
                            continue;
                        }
                        topBuilder.append(idx++).append(". ");
                        if (filePath != null) {
                            topBuilder.append("`").append(filePath).append("`");
                        }
                        if (fileDescription != null) {
                            if (filePath != null) {
                                topBuilder.append(" — ");
                            }
                            topBuilder.append(fileDescription);
                        }
                        topBuilder.append("\n");
                    }
                    topBuilder.append("\n");
                }
            }
        }
        if (topBuilder.length() == 0) {
            topBuilder.append("AI Code Review completed.\n\n");
        }
        topBuilder.append("---\n\nAuthor: '@AliasJeff'\n");
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
                // GitHub API 限制每次请求最多 100 个 inline comments，需要分批发送
                final int MAX_COMMENTS_PER_BATCH = 100;
                int totalComments = ordered.size();
                if (totalComments <= MAX_COMMENTS_PER_BATCH) {
                    createPullRequestReview(commitSha, "AI Code Review inline comments", ordered);
                } else {
                    logger.info("Comments count ({}) exceeds batch limit ({}), splitting into batches", totalComments, MAX_COMMENTS_PER_BATCH);
                    int batchNumber = 1;
                    int totalBatches = (totalComments + MAX_COMMENTS_PER_BATCH - 1) / MAX_COMMENTS_PER_BATCH;
                    for (int i = 0; i < ordered.size(); i += MAX_COMMENTS_PER_BATCH) {
                        int end = Math.min(i + MAX_COMMENTS_PER_BATCH, ordered.size());
                        List<ReviewComment> batch = ordered.subList(i, end);
                        String batchBody = String.format("AI Code Review inline comments (Batch %d/%d)", batchNumber, totalBatches);
                        createPullRequestReview(commitSha, batchBody, batch);
                        logger.info("Sent batch {}/{} with {} comments", batchNumber, totalBatches, batch.size());
                        batchNumber++;
                        // 如果不是最后一批，等待 3 秒以避免 API 速率限制
                        if (batchNumber <= totalBatches) {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                logger.warn("Interrupted while waiting between batches", e);
                                throw new RuntimeException("Interrupted while waiting between batches", e);
                            }
                        }
                    }
                }
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
     * 调用 ChatUtils 中的 getRagContext 方法
     *
     * @param code 代码内容（原始diff文本）
     * @return RAG context 字符串
     */
    private String getRagContext(String code) {
        if (this.repository == null || this.repository.isEmpty()) {
            logger.warn("Repository is empty, cannot get RAG context");
            return "";
        }

        logger.info("Getting RAG context via ChatUtils. repository={}, codeSize={}", this.repository, code != null ? code.length() : 0);

        // 调用 ChatUtils 中的 getRagContext 方法
        String ragContext = ChatUtils.getRagContext(code, this.repository);

        logger.info("RAG context retrieved. contextSize={}", ragContext.length());
        return ragContext;
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

