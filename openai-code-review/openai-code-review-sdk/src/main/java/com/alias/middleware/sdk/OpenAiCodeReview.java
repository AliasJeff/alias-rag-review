package com.alias.middleware.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alias.middleware.sdk.domain.service.impl.OpenAiCodeReviewService;
import com.alias.middleware.sdk.config.AppConfig;
import com.alias.middleware.sdk.infrastructure.git.GitCommand;
import com.alias.middleware.sdk.infrastructure.openai.IOpenAI;
import com.alias.middleware.sdk.infrastructure.openai.impl.ChatGLM;

public class OpenAiCodeReview {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiCodeReview.class);

    // ChatGLM 配置
    private String chatglm_apiHost = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private String chatglm_apiKeySecret = "";

    // Github 配置
    private String github_review_log_uri;
    private String github_token;

    // 工程配置 - 自动获取
    private String github_project;
    private String github_branch;
    private String github_author;

    public static void main(String[] args) throws Exception {
        AppConfig cfg = AppConfig.getInstance();

        // Prefer ChatGLM defaults unless overridden
        String chatglmHost = cfg.getString("chatglm", "apiHost");
        String chatglmKey = cfg.getString("chatglm", "apiKeySecret");

        String reviewLogUri = cfg.getString("github", "reviewLogUri");
        String githubToken = cfg.getString("github", "token");
        String project = cfg.getString("commit", "project");
        String branch = cfg.getString("commit", "branch");
        String author = cfg.getString("commit", "author");
        String message = cfg.getString("commit", "message");

        GitCommand gitCommand = new GitCommand(
                reviewLogUri,
                githubToken,
                project,
                branch,
                author,
                message
        );

        IOpenAI openAI = new ChatGLM(
                chatglmHost != null && !chatglmHost.isEmpty() ? chatglmHost : "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                cfg.requireString("chatglm", "apiKeySecret")
        );

        OpenAiCodeReviewService openAiCodeReviewService = new OpenAiCodeReviewService(gitCommand, openAI);
        openAiCodeReviewService.exec();

        logger.info("openai-code-review done!");
    }

}
