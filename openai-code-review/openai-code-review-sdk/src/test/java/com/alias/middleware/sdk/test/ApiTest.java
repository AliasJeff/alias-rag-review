package com.alias.middleware.sdk.test;

import org.junit.Test;

import com.alias.middleware.sdk.infrastructure.openai.IOpenAI;
import com.alias.middleware.sdk.domain.service.impl.ReviewPullRequestService;
import com.alias.middleware.sdk.infrastructure.openai.impl.ChatGLM;
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
        // 1) 解析 PR URL
        GitHubPrUtils.PrInfo info = GitHubPrUtils.parsePrUrl(url);

        // 2) 组装依赖
        // 即便 PR 评论不再写日志仓库，GitCommand 仍用于生成 diff
        GitCommand gitCommand = new GitCommand(
                // 以下参数用于旧的日志落库逻辑，这里给占位即可
                System.getenv().getOrDefault("GITHUB_REVIEW_LOG_URI",
                        "https://github.com/AliasJeff/openai-code-review-log"),
                System.getenv().getOrDefault("GITHUB_TOKEN", ""),
                System.getenv().getOrDefault("COMMIT_PROJECT", info.repo),
                System.getenv().getOrDefault("COMMIT_BRANCH", "main"),
                System.getenv().getOrDefault("COMMIT_AUTHOR", "ci"),
                System.getenv().getOrDefault("COMMIT_MESSAGE", "code review"));
        IOpenAI openAI = new ChatGLM(
                System.getenv().getOrDefault("CHATGLM_APIHOST",
                        "https://open.bigmodel.cn/api/paas/v4/chat/completions"),
                System.getenv("CHATGLM_APIKEYSECRET"));

        // 3) 执行 PR 代码审查
        ReviewPullRequestService svc = new ReviewPullRequestService(
                gitCommand, openAI);
        svc.setRepository(info.repository);
        svc.setPrNumber(info.prNumber);
        svc.exec();
    }

}
