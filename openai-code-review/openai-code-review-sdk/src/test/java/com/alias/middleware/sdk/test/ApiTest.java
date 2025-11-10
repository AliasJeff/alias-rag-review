package com.alias.middleware.sdk.test;

import org.junit.Test;

import com.alias.middleware.sdk.infrastructure.openai.IOpenAI;
import com.alias.middleware.sdk.domain.model.ModelEnum;
import com.alias.middleware.sdk.domain.service.impl.ReviewPullRequestService;
import com.alias.middleware.sdk.infrastructure.openai.impl.OpenAI;
import com.alias.middleware.sdk.infrastructure.git.GitCommand;
import com.alias.middleware.sdk.types.utils.BearerTokenUtils;
import com.alias.middleware.sdk.types.utils.GitHubPrUtils;

import javax.annotation.Resource;

public class ApiTest {

    @Resource
    private IOpenAI openAI;

    @Resource
    private ReviewPullRequestService reviewPullRequestService;

    @Resource
    private GitCommand gitCommand;

    @Resource
    private GitHubPrUtils gitHubPrUtils;

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
        String url = "https://github.com/AliasJeff/repo/pull/123";
        String baseRef = "main";
        String headRef = "feature-branch";
        
        // 组装依赖
        // 即便 PR 评论不再写日志仓库，GitCommand 仍用于生成 diff
        GitCommand gitCommand = new GitCommand(
                // 以下参数用于旧的日志落库逻辑，这里给占位即可
                System.getenv().getOrDefault("GITHUB_REVIEW_LOG_URI",
                        "https://github.com/AliasJeff/openai-code-review-log"),
                System.getenv().getOrDefault("GITHUB_TOKEN", ""),
                System.getenv().getOrDefault("COMMIT_PROJECT", "repo"),
                System.getenv().getOrDefault("COMMIT_BRANCH", "main"),
                System.getenv().getOrDefault("COMMIT_AUTHOR", "ci"),
                System.getenv().getOrDefault("COMMIT_MESSAGE", "code review"));

        // 使用 OpenAI GPT-4o
        IOpenAI openAI = new OpenAI(
                System.getenv().getOrDefault("OPENAI_APIHOST",
                        "https://api.openai.com/v1/chat/completions"),
                System.getenv("OPENAI_APIKEY"));

        // 执行 PR 代码审查
        ReviewPullRequestService reviewPullRequestService = new ReviewPullRequestService(gitCommand, openAI);
        reviewPullRequestService.setModel(ModelEnum.GPT_4O);
        reviewPullRequestService.exec(url, baseRef, headRef);
    }

}
