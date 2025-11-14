package com.alias.middleware.sdk.test;

import org.junit.Test;

import com.alias.middleware.sdk.infrastructure.openai.IOpenAI;
import com.alias.middleware.sdk.domain.model.ModelEnum;
import com.alias.middleware.sdk.domain.service.impl.ReviewPullRequestService;
import com.alias.middleware.sdk.infrastructure.openai.impl.OpenAI;
import com.alias.middleware.sdk.infrastructure.git.GitCommand;
import com.alias.middleware.sdk.utils.BearerTokenUtils;
import com.alias.middleware.sdk.utils.GitHubPrUtils;
import com.alias.middleware.sdk.config.AppConfig;


public class ApiTest {

    @Test
    public void test_parsePrUrl() {
        String url = "https://github.com/owner/repo/pull/123/files";
        GitHubPrUtils.PrInfo info = GitHubPrUtils.parsePrUrl(url);
        System.out.println(info);
        // 可用于设置到运行环境（示例打印代替设置）
        System.out.println("GITHUB_REPOSITORY=" + info.repository);
        System.out.println("GITHUB_PR_NUMBER=" + info.prNumber);
    }

    @Test
    public void test_reviewPullRequest() {
        AppConfig cfg = AppConfig.getInstance();
        // NOTE: 手动填写 PR URL
        String url = "https://github.com/AliasJeff/alias-rag-review/pull/3";

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

        System.out.println("AI Code review success!");
    }

}
