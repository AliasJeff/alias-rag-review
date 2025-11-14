package com.alias.middleware.sdk;

import com.alias.middleware.sdk.domain.model.ModelEnum;
import com.alias.middleware.sdk.domain.service.impl.ReviewPullRequestService;
import com.alias.middleware.sdk.infrastructure.openai.impl.OpenAI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alias.middleware.sdk.config.AppConfig;
import com.alias.middleware.sdk.infrastructure.git.GitCommand;
import com.alias.middleware.sdk.infrastructure.openai.IOpenAI;

public class OpenAiCodeReview {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiCodeReview.class);

    public static void main(String[] args) throws Exception {
        AppConfig cfg = AppConfig.getInstance();

        // NOTE: 手动填写 PR URL
        String url = "https://github.com/AliasJeff/alias-rag-review/pull/8";

        // 组装依赖
        // GitCommand 用于通过 GitHub API 获取 PR diff 和 commit SHA
        GitCommand gitCommand = new GitCommand(
                cfg.getString("github", "token")
        );

        // 使用 OpenAI GPT-4o
        IOpenAI openAI = new OpenAI(
                cfg.getString("openai", "apiHost"),
                cfg.getString("openai", "apiKey"));

        // 执行 PR 代码审查
        ReviewPullRequestService reviewPullRequestService = new ReviewPullRequestService(gitCommand, openAI);
        reviewPullRequestService.setModel(ModelEnum.GPT_4O);
        reviewPullRequestService.exec(url);

        logger.info("openai-code-review done!");
    }

}
