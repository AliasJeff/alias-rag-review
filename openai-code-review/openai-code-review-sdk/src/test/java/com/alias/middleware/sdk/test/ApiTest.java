package com.alias.middleware.sdk.test;

import org.junit.Test;

import com.alias.middleware.sdk.infrastructure.openai.IOpenAI;
import com.alias.middleware.sdk.domain.model.ModelEnum;
import com.alias.middleware.sdk.domain.service.impl.ReviewPullRequestService;
import com.alias.middleware.sdk.infrastructure.openai.impl.OpenAI;
import com.alias.middleware.sdk.infrastructure.git.GitCommand;
import com.alias.middleware.sdk.types.utils.BearerTokenUtils;
import com.alias.middleware.sdk.types.utils.GitHubPrUtils;
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

    public static void main(String[] args) {
        String apiKeySecret = "c78fbacd3e10118ad5649d7a54a3a163.UunYDBxpzeClvSKZ";
        String token = BearerTokenUtils.getToken(apiKeySecret);
        System.out.println(token);
    }

    @Test
    public void test_reviewPullRequest() {
        AppConfig cfg = AppConfig.getInstance();
        String baseRef = "main";
        // NOTE: 手动填写 headRef
        String headRef = "20251110-review-pull-request";
        // NOTE: 手动填写 PR URL
        String url = "https://github.com/AliasJeff/alias-rag-review/pull/1";

        // 组装依赖
        // 即便 PR 评论不再写日志仓库，GitCommand 仍用于生成 diff
        GitCommand gitCommand = new GitCommand(
                // 以下参数用于旧的日志落库逻辑，这里给占位即可
                cfg.getString("github", "reviewLogUri"),
                cfg.getString("github", "token"),
                cfg.getString("commit", "project"),
                cfg.getString("commit", "branch"),
                cfg.getString("commit", "author"),
                cfg.getString("commit", "message"));

        // 使用 OpenAI GPT-4o
        IOpenAI openAI = new OpenAI(
                cfg.getString("openai", "apiHost"),
                cfg.getString("openai", "apiKey"));

        // 执行 PR 代码审查
        ReviewPullRequestService reviewPullRequestService = new ReviewPullRequestService(gitCommand, openAI);
        reviewPullRequestService.setModel(ModelEnum.GPT_4O);
        reviewPullRequestService.exec(url, baseRef, headRef);
    }

}
