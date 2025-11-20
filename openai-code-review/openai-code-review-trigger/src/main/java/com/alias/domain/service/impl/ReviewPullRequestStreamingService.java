package com.alias.domain.service.impl;

import com.alias.config.AppConfig;
import com.alias.domain.model.Message;
import com.alias.domain.model.ModelEnum;
import com.alias.domain.model.PrSnapshot;
import com.alias.domain.prompt.ReviewPrompts;
import com.alias.domain.service.AbstractOpenAiCodeReviewService;
import com.alias.domain.service.IMessageService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Streaming version of ReviewPullRequestService
 * Supports SSE streaming for real-time PR review feedback
 */
public class ReviewPullRequestStreamingService extends AbstractOpenAiCodeReviewService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewPullRequestStreamingService.class);

    // PR 相关配置由调用方设置，不从环境变量读取
    private String repository; // owner/repo
    private String prNumber;   // 数字字符串
    private String prUrl;      // PR URL
    private String conversationId; // conversation identifier for SSE payloads
    private String model;      // 模型名称，默认使用 GPT-4o

    private ChatClient chatClient;
    private final IPrSnapshotService prSnapshotService;
    private final IMessageService messageService;
    private UUID clientIdentifier;

    public ReviewPullRequestStreamingService(GitCommand gitCommand, ChatClient chatClient) {
        this(gitCommand, chatClient, null, null);
    }

    public ReviewPullRequestStreamingService(GitCommand gitCommand, ChatClient chatClient, IPrSnapshotService prSnapshotService) {
        this(gitCommand, chatClient, prSnapshotService, null);
    }

    public ReviewPullRequestStreamingService(GitCommand gitCommand, ChatClient chatClient, IPrSnapshotService prSnapshotService, IMessageService messageService) {
        super(gitCommand, null);
        this.chatClient = chatClient;
        this.prSnapshotService = prSnapshotService;
        this.messageService = messageService;
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

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public void setClientIdentifier(UUID clientIdentifier) {
        this.clientIdentifier = clientIdentifier;
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

    /**
     * 流式执行 PR 审查
     *
     * @param emitter SSE 发射器
     * @throws Exception 如果审查失败
     */
    public void execStreaming(SseEmitter emitter) throws Exception {
        logger.info("Starting streaming PR review. prUrl={}", this.prUrl);
        try {
            // 获取 diff
            String diffCode = getDiffCode();

            // 流式进行代码审查，获取整合后的review结果
            String mergedReviewJson = codeReviewStreaming(diffCode, emitter);

            // 记录审查结果到 GitHub PR
            logger.info("Recording review results to GitHub PR");
            String recordedUrl = recordCodeReview(mergedReviewJson);
            logger.info("Review results recorded to GitHub PR. url={}", recordedUrl);

            // 保存 message 到数据库
            saveReviewMessage(mergedReviewJson);

            // 发送完成事件
            emitter.send(SseEmitter.event().name("complete").data("Streaming completed"));
            emitter.complete();

            logger.info("Streaming PR review completed. prUrl={}", this.prUrl);
        } catch (IOException e) {
            logger.error("Streaming error", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("Error sending error event", ex);
            }
        } catch (Exception e) {
            logger.error("Unexpected error in streaming review", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("Error sending error event", ex);
            }
        }
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
        // 同步版本，不使用此方法
        throw new UnsupportedOperationException("Use codeReviewStreaming instead");
    }

    /**
     * 流式代码审查
     *
     * @param diffCode 代码差异
     * @param emitter  SSE 发射器
     * @return 整合后的review结果JSON字符串
     * @throws Exception 如果审查失败
     */
    private String codeReviewStreaming(String diffCode, SseEmitter emitter) throws Exception {
        logger.info("Submitting diff to LLM for streaming review. model={}, diffSize={}", this.model != null ? this.model : ModelEnum.GPT_4O.getCode(), diffCode != null ? diffCode.length() : 0);
        final int MAX_PROMPT_CHARS = 180_000;
        String safeDiff = diffCode == null ? "" : diffCode;
        ObjectMapper mapper = new ObjectMapper();

        // 使用 VCSUtils 将 diff 解析为结构化对象
        List<VCSUtils.FileChanges> files;
        try {
            files = VCSUtils.parseUnifiedDiff(safeDiff);
        } catch (Exception e) {
            logger.warn("Failed to parse unified diff; fallback to raw diff. err={}", e.toString());
            files = new ArrayList<>();
        }

        if (files.isEmpty()) {
            logger.warn("No files found in diff, returning empty review");
            String emptyMsg = "### ℹ️ No Code Changes Detected\n\n" + "No code file changes detected in the current PR.\n\n";
            emitter.send(SseEmitter.event().name("review").data(buildEmitterPayload(emptyMsg)));
            // 返回空的review结果JSON
            Map<String, Object> emptyReview = createEmptyReview();
            return mapper.writeValueAsString(emptyReview);
        }

        persistSnapshotAsync(files);

        // 获取 RAG context
        String ragContext = getRagContext(safeDiff);
        String ragMsg = "🧠 **RAG Context Loaded** (Size: " + (ragContext != null ? ragContext.length() : 0) + " characters)\n\n";
        emitter.send(SseEmitter.event().name("rag_context_success").data(buildEmitterPayload(ragMsg)));

        // 步骤1: 先进行整体PR摘要
        logger.info("Starting PR overall summary. totalFiles={}", files.size());
        String summaryStartMsg = "### 📋 Generating PR Summary\n\n" + "Analyzing overall PR changes...\n\n";
        emitter.send(SseEmitter.event().name("pr_summary_start").data(buildEmitterPayload(summaryStartMsg)));

        JsonNode prSummaryJson = null;
        try {
            String prSummaryResponse = generatePrSummary(safeDiff, ragContext, MAX_PROMPT_CHARS);
            try {
                prSummaryJson = mapper.readTree(prSummaryResponse);
            } catch (Exception parseErr) {
                logger.warn("Failed to parse PR summary JSON, attempting to extract. err={}", parseErr.toString());
                String cleaned = ReviewJsonUtils.extractJsonPayload(prSummaryResponse);
                prSummaryJson = mapper.readTree(cleaned);
            }

            // 发送PR摘要信息
            JsonNode prSummary = prSummaryJson.get("pr_summary");
            if (prSummary != null) {
                String title = ReviewJsonUtils.safeText(prSummary, "title");
                String description = ReviewJsonUtils.safeText(prSummary, "description");

                if (title != null && !title.isEmpty()) {
                    String titleMsg = "### 📌 PR Title\n\n**" + title + "**\n\n";
                    emitter.send(SseEmitter.event().name("pr_summary").data(buildEmitterPayload(titleMsg)));
                }

                if (description != null && !description.isEmpty()) {
                    String descMsg = "### 📝 PR Description\n\n" + description + "\n\n";
                    emitter.send(SseEmitter.event().name("pr_summary").data(buildEmitterPayload(descMsg)));
                }

                JsonNode keyChanges = prSummary.get("key_changes");
                if (keyChanges != null && keyChanges.isArray()) {
                    StringBuilder keyChangesMsg = new StringBuilder("### 🔑 Key Changes\n\n");
                    Iterator<JsonNode> it = keyChanges.elements();
                    int idx = 1;
                    while (it.hasNext()) {
                        String change = it.next().asText();
                        keyChangesMsg.append(idx++).append(". ").append(change).append("\n");
                    }
                    keyChangesMsg.append("\n");
                    emitter.send(SseEmitter.event().name("pr_summary").data(buildEmitterPayload(keyChangesMsg.toString())));
                }
            }

            logger.info("PR summary generated successfully");
        } catch (Exception e) {
            logger.error("Failed to generate PR summary, continuing with per-file review. err={}", e.toString(), e);
            String errorMsg = "⚠️ **PR Summary Generation Failed**\n\nContinuing with per-file review...\n\n";
            emitter.send(SseEmitter.event().name("pr_summary_error").data(buildEmitterPayload(errorMsg)));
        }

        // 步骤2: 遍历每个文件，分别进行流式review
        List<JsonNode> fileReviews = new ArrayList<>();
        List<JsonNode> allComments = new ArrayList<>();

        logger.info("Starting per-file streaming review. totalFiles={}", files.size());
        String startMsg = "### 📄 Starting Per-File Review\n\n" + "**Total Files:** " + files.size() + "\n\n";
        emitter.send(SseEmitter.event().name("review_start").data(buildEmitterPayload(startMsg)));
        for (int i = 0; i < files.size(); i++) {
            VCSUtils.FileChanges file = files.get(i);
            logger.info("Reviewing file {}/{}. path={}", i + 1, files.size(), file.path);
            String displayPath = shortenPath(file.path);
            String fileStartMsg = "#### 📂 Reviewing File [" + (i + 1) + "/" + files.size() + "]\n\n" + "**File Path:** `" + displayPath + "`\n\n";
            emitter.send(SseEmitter.event().name("file_start").data(buildEmitterPayload(fileStartMsg)));

            try {
                // 对单个文件进行流式review
                String fileReviewJson = reviewSingleFileStreaming(file, ragContext, MAX_PROMPT_CHARS, emitter);

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

                logger.info("Completed streaming review for file {}/{}. path={}, comments={}", i + 1, files.size(), file.path, comments != null && comments.isArray() ? comments.size() : 0);
            } catch (Exception e) {
                logger.error("Failed to review file. path={}, err={}", file.path, e.toString(), e);
                // 发送文件审查错误事件
                Map<String, Object> fileErrorEvent = new HashMap<>();
                fileErrorEvent.put("file", displayPath);
                fileErrorEvent.put("error", e.getMessage());
                try {
                    String fileErrorMsg = "##### ❌ File Review Failed\n\n" + "**File:** `" + displayPath + "`\n" + "**Error:** " + e.getMessage() + "\n\n";
                    emitter.send(SseEmitter.event().name("file_error").data(buildEmitterPayload(fileErrorMsg)));
                } catch (IOException ex) {
                    logger.error("Error sending file error event", ex);
                }
            }
        }

        // 将 JsonNode 列表转换为 Map 列表
        List<Map<String, Object>> commentsList = new ArrayList<>();
        for (JsonNode comment : allComments) {
            Map<String, Object> commentMap = mapper.convertValue(comment, new TypeReference<>() {
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

        // Send final review results
        String summaryHeader = "\n---\n\n## 🎉 Review Complete\n\n";
        emitter.send(SseEmitter.event().name("review_summary").data(buildEmitterPayload(summaryHeader)));

        if (!commentsList.isEmpty()) {
            String commentsHeader = "### 💬 Detailed Comments (" + commentsList.size() + " items)\n\n";
            emitter.send(SseEmitter.event().name("review_summary").data(buildEmitterPayload(commentsHeader)));

            for (int idx = 0; idx < commentsList.size(); idx++) {
                Map<String, Object> comment = commentsList.get(idx);
                String severity = comment.get("severity") != null ? comment.get("severity").toString() : "info";
                String severityEmoji = getSeverityEmoji(severity);
                String commentMsg = "#### " + severityEmoji + " Comment " + (idx + 1) + "\n\n" + "**File:** `" + comment.get("path") + "`\n" + "**Line:** " + comment.get("line") + "\n" + "**Severity:** " + severity + "\n\n" + comment.get("body") + "\n\n";
                emitter.send(SseEmitter.event().name("review_summary").data(buildEmitterPayload(commentMsg)));
            }
        }

        logger.info("Completed per-file streaming review. totalFiles={}, totalComments={}", files.size(), allComments.size());
        String completeMsg = "\n---\n\n✅ **Review Complete** | Files: " + files.size() + " | Comments: " + allComments.size() + "\n\n";
        emitter.send(SseEmitter.event().name("review_complete").data(buildEmitterPayload(completeMsg)));

        // 将整合后的review结果序列化为JSON字符串并返回
        String mergedReviewJson = mapper.writeValueAsString(mergedReview);
        logger.debug("Merged review JSON generated. size={}", mergedReviewJson.length());
        return mergedReviewJson;
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
                logger.info("Persisted PR snapshot asynchronously (streaming). url={}, files={}", snapshotUrl, snapshotFiles.size());
            } catch (Exception e) {
                logger.warn("Failed to persist PR snapshot (streaming). url={}, err={}", snapshotUrl, e.getMessage(), e);
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
     * @param diffCode       代码差异
     * @param ragContext     RAG上下文
     * @param maxPromptChars 最大prompt字符数限制
     * @return PR摘要的JSON字符串
     * @throws Exception 如果生成失败
     */
    private String generatePrSummary(String diffCode, String ragContext, int maxPromptChars) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // 将所有文件转换为JSON
        List<VCSUtils.FileChanges> allFilesList;
        try {
            allFilesList = VCSUtils.parseUnifiedDiff(diffCode);
        } catch (Exception e) {
            logger.warn("Failed to parse unified diff for summary; fallback to empty list. err={}", e.toString());
            allFilesList = new ArrayList<>();
        }

        String structuredJson = mapper.writeValueAsString(allFilesList);

        String basePrompt = ReviewPrompts.PR_SUMMARY_PROMPT;
        // 将占位符替换为结构化 JSON 和 RAG context
        String mergedPrompt = basePrompt.replace("<Git diff>", structuredJson).replace("<RAG context>", ragContext != null && !ragContext.isEmpty() ? ragContext : "No additional context available.");

        if (mergedPrompt.length() > maxPromptChars) {
            logger.warn("Prompt too large for PR summary. promptSize={}, maxSize={}", mergedPrompt.length(), maxPromptChars);
        }

        logger.debug("Request for PR summary");

        // Build messages for ChatClient
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new UserMessage(mergedPrompt));

        // Create prompt with model options
        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder().model(this.model != null ? this.model : ModelEnum.GPT_4O.getCode()).build());

        // Call ChatClient (non-streaming for summary)
        org.springframework.ai.chat.model.ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        String content = response.getResult().getOutput().getText();

        logger.debug("PR summary response. contentSize={}", content != null ? content.length() : 0);

        return content;
    }

    /**
     * 对单个文件进行流式review
     *
     * @param file           文件变更对象
     * @param ragContext     RAG上下文
     * @param maxPromptChars 最大prompt字符数限制
     * @param emitter        SSE 发射器
     * @return review结果的JSON字符串
     * @throws Exception 如果review失败
     */
    private String reviewSingleFileStreaming(VCSUtils.FileChanges file, String ragContext, int maxPromptChars, SseEmitter emitter) throws Exception {
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

        // Call ChatClient with streaming
        StringBuilder fullResponse = new StringBuilder();
        chatClient.prompt(prompt).stream().content().doOnNext(chunk -> {
            fullResponse.append(chunk);
            // 发送流式内容块
            // TODO 暂时注释
            // emitter.send(SseEmitter.event().name("review_chunk").data(buildEmitterPayload(chunk)));
        }).blockLast();

        logger.debug("Review response for file: {}, contentSize={}", file.path, fullResponse.length());

        return fullResponse.toString();
    }

    /**
     * 创建空的review结果
     */
    @SuppressWarnings("unused")
    private Map<String, Object> createEmptyReview() {
        Map<String, Object> review = new HashMap<>();
        review.put("comments", new ArrayList<>());
        return review;
    }

    private String buildEmitterPayload(String content) {
        String safeContent = content != null ? content : "";
        String safeConversationId = conversationId != null ? conversationId : "";
        return "{\"content\":\"" + escapeJson(safeContent) + "\",\"conversationId\":\"" + escapeJson(safeConversationId) + "\"}";
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    @Override
    protected String recordCodeReview(String recommend) throws Exception {
        logger.info("Posting review to GitHub PR. repository={}, prNumber={}", this.repository, this.prNumber);
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
        }
        String combinedTop = topBuilder.length() > 0 ? topBuilder.toString() : "AI Code Review completed.";
        postCommentToGithubPr(combinedTop);

        // Inline comments
        JsonNode comments = root.get("comments");
        if (comments != null && comments.isArray() && comments.size() > 0) {
            String commitSha = gitCommand.getPrHeadCommitSha(this.repository, this.prNumber);
            List<ReviewComment> rankedComments = new ArrayList<>();
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
                rankedComments.add(new ReviewComment(new ReviewCommentDetail(path, "RIGHT", line, fullBody), rank, seq++));
            }
            if (!rankedComments.isEmpty()) {
                rankedComments.sort((a, b) -> {
                    if (a.rank != b.rank) return Integer.compare(a.rank, b.rank);
                    return Integer.compare(a.index, b.index);
                });
                List<ReviewCommentDetail> ordered = new ArrayList<>();
                for (ReviewComment rc : rankedComments) {
                    ordered.add(rc.comment);
                }
                // GitHub API 限制每次请求最多 10 个 inline comments，需要分批发送
                final int MAX_COMMENTS_PER_BATCH = 10;
                int totalComments = ordered.size();
                if (totalComments <= MAX_COMMENTS_PER_BATCH) {
                    createPullRequestReview(commitSha, "AI Code Review inline comments", ordered);
                } else {
                    logger.info("Comments count ({}) exceeds batch limit ({}), splitting into batches", totalComments, MAX_COMMENTS_PER_BATCH);
                    int batchNumber = 1;
                    int totalBatches = (totalComments + MAX_COMMENTS_PER_BATCH - 1) / MAX_COMMENTS_PER_BATCH;
                    for (int i = 0; i < ordered.size(); i += MAX_COMMENTS_PER_BATCH) {
                        int end = Math.min(i + MAX_COMMENTS_PER_BATCH, ordered.size());
                        List<ReviewCommentDetail> batch = ordered.subList(i, end);
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
    protected void pushMessage(String logUrl) {
        // TODO: not implemented
    }

    /**
     * 从 RAG 服务获取代码上下文
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
        logger.info("Comment posted to GitHub PR successfully. code={}, url=https://github.com/{}/pull/{}", code, repo, this.prNumber);
        return "https://github.com/" + repo + "/pull/" + this.prNumber;
    }

    private void createPullRequestReview(String commitSha, String body, List<ReviewCommentDetail> comments) throws Exception {
        String repo = this.repository;
        String token = AppConfig.getInstance().requireString("github", "token");
        if (repo == null || repo.isEmpty()) {
            throw new RuntimeException("GITHUB_REPOSITORY is empty");
        }
        if (token.isEmpty()) {
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
            ReviewCommentDetail c = comments.get(i);
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

    private String getSeverityEmoji(String severity) {
        if (severity == null) return "🔎";
        String lower = severity.toLowerCase();
        if (lower.contains("critical")) return "🛑";
        if (lower.contains("major")) return "⚠️";
        if (lower.contains("minor")) return "ℹ️";
        if (lower.contains("suggestion")) return "💡";
        return "🔎";
    }

    private String shortenPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String normalized = path.replace("\\", "/");
        String[] rawParts = normalized.split("/");
        List<String> parts = new ArrayList<>();
        for (String part : rawParts) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            return normalized;
        }
        int start = Math.max(parts.size() - 3, 0);
        return String.join("/", parts.subList(start, parts.size()));
    }

    /**
     * 保存 review 结果到数据库
     *
     * @param reviewJson review 结果的 JSON 字符串
     */
    private void saveReviewMessage(String reviewJson) {
        if (messageService == null) {
            logger.debug("MessageService is not available, skipping message save");
            return;
        }

        if (conversationId == null || conversationId.isEmpty()) {
            logger.debug("ConversationId is empty, skipping message save");
            return;
        }

        try {
            UUID conversationUuid = UUID.fromString(conversationId);

            // 构建 message 内容，包含 review 摘要信息
            ObjectMapper mapper = new ObjectMapper();
            JsonNode reviewNode;
            try {
                reviewNode = mapper.readTree(reviewJson);
            } catch (Exception e) {
                logger.warn("Failed to parse review JSON for message, using raw content. err={}", e.toString());
                reviewNode = null;
            }

            StringBuilder contentBuilder = new StringBuilder();
            if (reviewNode != null) {
                // 从PR摘要中获取信息
                JsonNode prSummary = reviewNode.get("pr_summary");
                if (prSummary != null) {
                    String title = ReviewJsonUtils.safeText(prSummary, "title");
                    String description = ReviewJsonUtils.safeText(prSummary, "description");
                    if (title != null && !title.isEmpty()) {
                        contentBuilder.append("### PR Title: ").append(title).append("\n\n");
                    }
                    if (description != null && !description.isEmpty()) {
                        contentBuilder.append("### PR Description\n\n").append(description).append("\n\n");
                    }
                }
                // 添加评论数量信息
                JsonNode comments = reviewNode.get("comments");
                if (comments != null && comments.isArray()) {
                    contentBuilder.append("### Comments: ").append(comments.size()).append(" items\n\n");
                }
            } else {
                // 如果无法解析 JSON，使用原始内容
                contentBuilder.append(reviewJson);
            }

            // 构建 metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("pr_url", this.prUrl);
            metadata.put("repository", this.repository);
            metadata.put("pr_number", this.prNumber);
            metadata.put("model", this.model);

            // 创建并保存 message
            Message message = Message.builder().conversationId(conversationUuid).role("assistant").type("code_review").content(contentBuilder.toString()).metadata(metadata).build();

            messageService.createMessage(message);
            logger.info("Review message saved to database. conversationId={}", conversationId);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid conversationId format, skipping message save. conversationId={}, err={}", conversationId, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to save review message to database. conversationId={}, err={}", conversationId, e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }

    private static final class ReviewCommentDetail {
        final String path;
        final String side; // "RIGHT" or "LEFT"
        final int line;
        final String body;

        ReviewCommentDetail(String path, String side, int line, String body) {
            this.path = path;
            this.side = side;
            this.line = line;
            this.body = body;
        }
    }

    private static final class ReviewComment {
        final ReviewCommentDetail comment;
        final int rank;
        final int index;

        ReviewComment(ReviewCommentDetail comment, int rank, int index) {
            this.comment = comment;
            this.rank = rank;
            this.index = index;
        }
    }
}
