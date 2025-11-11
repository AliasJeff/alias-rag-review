package com.alias.middleware.sdk.domain.service.impl;

import com.alias.middleware.sdk.domain.model.ModelEnum;
import com.alias.middleware.sdk.domain.service.AbstractOpenAiCodeReviewService;
import com.alias.middleware.sdk.infrastructure.git.GitCommand;
import com.alias.middleware.sdk.infrastructure.openai.IOpenAI;
import com.alias.middleware.sdk.infrastructure.openai.dto.ChatCompletionRequestDTO;
import com.alias.middleware.sdk.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;
import com.alias.middleware.sdk.utils.GitHubPrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alias.middleware.sdk.config.AppConfig;
import com.alias.middleware.sdk.domain.prompt.ReviewPrompts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alias.middleware.sdk.utils.DiffLineMapper;
import com.alias.middleware.sdk.utils.ReviewJsonUtils;
import com.alias.middleware.sdk.utils.ReviewCommentUtils;
import com.alias.middleware.sdk.utils.IoUtils;
import com.alias.middleware.sdk.utils.DiffChunkUtils;
import com.alias.middleware.sdk.utils.SeverityUtils;

import java.io.IOException;
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
        final int MAX_PROMPT_CHARS = 180_000; // 粗略上限，避免超出供应商限制
        String safeDiff = diffCode == null ? "" : diffCode;
        String basePrompt = ReviewPrompts.PR_REVIEW_PROMPT;
        String mergedPrompt = basePrompt.replace("<Git diff>", safeDiff);
        if (mergedPrompt.length() <= MAX_PROMPT_CHARS) {
            ChatCompletionRequestDTO chatCompletionRequest = new ChatCompletionRequestDTO();
            chatCompletionRequest.setModel(this.model != null ? this.model : ModelEnum.GPT_4O.getCode());
            chatCompletionRequest.setMessages(new ArrayList<ChatCompletionRequestDTO.Prompt>() {
                private static final long serialVersionUID = -7988151926241837899L;
                {
                    add(new ChatCompletionRequestDTO.Prompt("user", mergedPrompt));
                }
            });
            ChatCompletionSyncResponseDTO completions = openAI.completions(chatCompletionRequest);
            ChatCompletionSyncResponseDTO.Message message = completions.getChoices().get(0).getMessage();
            logger.info("Received review response from LLM. contentSize={}", message != null && message.getContent() != null ? message.getContent().length() : 0);
            logger.debug("Review response: {}", message.getContent());
            return message.getContent();
        }
        // 分片处理：按文件块切分 diff 并合并结果
        logger.info("Prompt too large, performing chunked review.");
        List<String> chunks = DiffChunkUtils.splitDiffByFileBlocks(safeDiff, MAX_PROMPT_CHARS - basePrompt.length() - 10_000);
        List<JsonNode> partResults = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        int idx = 0;
        for (String part : chunks) {
            idx++;
            String partInstruction = "【分片 " + idx + "/" + chunks.size() + "】以下仅是本次 PR diff 的一部分。请严格按既定 JSON 输出格式返回，建议只输出本分片涉及到的 comments；overall_score 与 summary 可按分片视角给出。\n\n";
            String partPrompt = partInstruction + basePrompt.replace("<Git diff>", part);
            ChatCompletionRequestDTO chatCompletionRequest = new ChatCompletionRequestDTO();
            chatCompletionRequest.setModel(this.model != null ? this.model : ModelEnum.GPT_4O.getCode());
            chatCompletionRequest.setMessages(new ArrayList<ChatCompletionRequestDTO.Prompt>() {
                private static final long serialVersionUID = -7988151926241837899L;
                {
                    add(new ChatCompletionRequestDTO.Prompt("user", partPrompt));
                }
            });
            ChatCompletionSyncResponseDTO completions = openAI.completions(chatCompletionRequest);
            ChatCompletionSyncResponseDTO.Message message = completions.getChoices().get(0).getMessage();
            String content = message != null ? message.getContent() : null;
            logger.info("Chunk {} response size={}", idx, content != null ? content.length() : 0);
            String jsonPayload;
            try {
                mapper.readTree(content);
                jsonPayload = content;
            } catch (Exception e) {
                jsonPayload = ReviewJsonUtils.extractJsonPayload(content != null ? content : "");
            }
            try {
                partResults.add(mapper.readTree(jsonPayload));
            } catch (Exception e) {
                logger.warn("Skip invalid chunk {} JSON. err={}", idx, e.toString());
            }
        }
        // 聚合：comments 合并，overall_score 取平均，summary 合并
        int countScore = 0;
        int sumScore = 0;
        StringBuilder mergedSummary = new StringBuilder();
        List<JsonNode> allComments = new ArrayList<>();
        for (JsonNode pr : partResults) {
            Integer s = ReviewJsonUtils.safeInt(pr, "overall_score");
            if (s != null) {
                sumScore += s;
                countScore++;
            }
            String sm = ReviewJsonUtils.safeText(pr, "summary");
            if (sm != null && !sm.isEmpty()) {
                if (mergedSummary.length() > 0) mergedSummary.append("\n");
                mergedSummary.append("• ").append(sm);
            }
            JsonNode cs = pr.get("comments");
            if (cs != null && cs.isArray()) {
                for (JsonNode c : cs) {
                    allComments.add(c);
                }
            }
        }
        int finalScore = countScore > 0 ? Math.max(0, Math.min(100, Math.round((float) sumScore / countScore))) : 70;
        String finalSummary = mergedSummary.length() > 0 ? mergedSummary.toString() : "分片审查汇总：未提供摘要。";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"overall_score\":").append(finalScore).append(",");
        sb.append("\"summary\":").append(ReviewJsonUtils.toJsonString(finalSummary)).append(",");
        sb.append("\"comments\":[");
        for (int i = 0; i < allComments.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(allComments.get(i).toString());
        }
        sb.append("]}");
        String aggregated = sb.toString();
        logger.info("Aggregated chunked review. commentsCount={}, finalScore={}", allComments.size(), finalScore);
        return aggregated;
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
            topBuilder.append("### 😀 整体评分\n").append("⭐️ ").append(overallScore).append("/100").append("\n\n");
        }
        topBuilder.append(ReviewCommentUtils.buildTopLevelComment(summary, general));
        String combinedTop = topBuilder.toString();
        postCommentToGithubPr(combinedTop);

        // Inline comments
        JsonNode comments = root.get("comments");
        if (comments != null && comments.isArray() && comments.size() > 0) {
            // Build diff index for head-side line validation/fix
            String unifiedDiff;
            DiffLineMapper.Index diffIndex = null;
            try {
                unifiedDiff = getDiffCode();
                diffIndex = DiffLineMapper.index(unifiedDiff != null ? unifiedDiff : "");
                logger.info("Diff index built for head line validation. validPaths={}", diffIndex != null ? "yes" : "no");
            } catch (Exception e) {
                logger.warn("Failed to build diff index; will skip line auto-fix. err={}", e.toString());
            }
            // Ensure refs are fetched and resolve commit sha of head
            gitCommand.fetchRemoteRef("origin", this.headRef);
            String commitSha = gitCommand.getRemoteRefCommitSha("origin", this.headRef);
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
                // Validate and optionally fix head-side line using diff index
                int effectiveLine = line;
                if (diffIndex != null) {
                    boolean valid = DiffLineMapper.isValidHeadLine(diffIndex, path, line);
                    if (!valid) {
                        int fixed = DiffLineMapper.fixHeadLineOrOriginal(diffIndex, path, line);
                        if (fixed <= 0) {
                            // skip comments that cannot be mapped to a valid head line
                            logger.info("Skip comment due to invalid head line and no fix. path={}, requestedLine={}", path, line);
                            continue;
                        }
                        effectiveLine = fixed;
                    }
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
                    fullBody = fullBody + "\n\n```suggestion\n" + suggestion + "\n```";
                }
                int rank = SeverityUtils.severityRank(severity);
                rankedComments.add(new RankedReviewComment(new ReviewComment(path, "RIGHT", effectiveLine, fullBody), rank, seq++));
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
        conn.setRequestProperty("User-Agent", "alias-openai-code-review-sdk");
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
            sb.append("{")
                    .append("\"path\":").append(ReviewJsonUtils.toJsonString(c.path)).append(",")
                    .append("\"side\":").append(ReviewJsonUtils.toJsonString(c.side)).append(",")
                    .append("\"line\":").append(c.line).append(",")
                    .append("\"body\":").append(ReviewJsonUtils.toJsonString(c.body))
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


