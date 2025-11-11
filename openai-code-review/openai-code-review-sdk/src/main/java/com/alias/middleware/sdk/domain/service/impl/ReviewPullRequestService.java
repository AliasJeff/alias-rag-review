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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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
        execGit(new String[]{"git", "fetch", "origin", base}, new File("."));
        execGit(new String[]{"git", "fetch", "origin", head}, new File("."));

        // 使用三点语法获取 merge-base 到 head 的变更
        String diff = execGitAndCapture(new String[]{"git", "diff", "origin/" + base + "...origin/" + head}, new File("."));
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
                add(new ChatCompletionRequestDTO.Prompt("user", "你是一位资深编程专家，拥有深厚的编程基础和广泛的技术栈知识。你的专长在于识别代码中的低效模式、安全隐患、以及可维护性问题，并能提出针对性的优化策略。你擅长以易于理解的方式解释复杂的概念，确保即使是初学者也能跟随你的指导进行有效改进。在提供优化建议时，你注重平衡性能、可读性、安全性、逻辑错误、异常处理、边界条件，以及可维护性方面的考量，同时尊重原始代码的设计意图。\n" +
                        "你总是以鼓励和建设性的方式提出反馈，致力于提升团队的整体编程水平，详尽指导编程实践，雕琢每一行代码至臻完善。用户会将仓库代码分支修改代码给你，以git diff 字符串的形式提供，你需要根据变化的代码，帮忙review本段代码。然后你review内容的返回内容必须严格遵守下面我给你的格式，包括标题内容。\n" +
                        "模板中的变量内容解释：\n" +
                        "变量1是给review打分，分数区间为0~100分。\n" +
                        "变量2 是code review发现的问题点，包括：可能的性能瓶颈、逻辑缺陷、潜在问题、安全风险、命名规范、注释、以及代码结构、异常情况、边界条件、资源的分配与释放等等\n" +
                        "变量3是具体的优化修改建议。\n" +
                        "变量4是你给出的修改后的代码。 \n" +
                        "变量5是代码中的优点。\n" +
                        "变量6是代码的逻辑和目的，识别其在特定上下文中的作用和限制\n" +
                        "\n" +
                        "必须要求：\n" +
                        "1. 以精炼的语言、严厉的语气指出存在的问题。\n" +
                        "2. 你的反馈内容必须使用严谨的markdown格式\n" +
                        "3. 不要携带变量内容解释信息。\n" +
                        "4. 有清晰的标题结构\n" +
                        "返回格式严格如下：\n" +
                        "# 项目： OpenAi 代码评审.\n" +
                        "### \uD83D\uDE00代码评分：{变量1}\n" +
                        "#### \uD83D\uDE00代码逻辑与目的：\n" +
                        "{变量6}\n" +
                        "#### ✅代码优点：\n" +
                        "{变量5}\n" +
                        "#### \uD83E\uDD14问题点：\n" +
                        "{变量2}\n" +
                        "#### \uD83C\uDFAF修改建议：\n" +
                        "{变量3}\n" +
                        "#### \uD83D\uDCBB修改后的代码：\n" +
                        "{变量4}\n" +
                        "`;代码如下:"));
                add(new ChatCompletionRequestDTO.Prompt("user", diffCode));
            }
        });

        ChatCompletionSyncResponseDTO completions = openAI.completions(chatCompletionRequest);
        ChatCompletionSyncResponseDTO.Message message = completions.getChoices().get(0).getMessage();
        logger.info("Received review response from LLM. contentSize={}", message != null && message.getContent() != null ? message.getContent().length() : 0);
        return message.getContent();
    }

    @Override
    protected String recordCodeReview(String recommend) throws Exception {
        logger.info("Posting review comment to GitHub PR. repository={}, prNumber={}", this.repository, this.prNumber);
        return postCommentToGithubPr(recommend);
    }

    @Override
    protected void pushMessage(String logUrl) throws Exception {
        // TODO: not implemented
    }

    private void execGit(String[] command, File directory) throws IOException, InterruptedException {
        logger.info("Executing git command: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(directory);
        Process p = pb.start();
        try (BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line;
            while ((line = err.readLine()) != null) {
                logger.debug(line);
            }
        }
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", command) + ", exit=" + exit);
        }
        logger.info("Git command finished successfully: {}", String.join(" ", command));
    }

    private String execGitAndCapture(String[] command, File directory) throws IOException, InterruptedException {
        logger.info("Executing git command (capture): {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(directory);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
        }
        try (BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line;
            while ((line = err.readLine()) != null) {
                logger.debug(line);
            }
        }
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", command) + ", exit=" + exit);
        }
        logger.info("Git command (capture) finished successfully: {}", String.join(" ", command));
        return out.toString();
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
}


