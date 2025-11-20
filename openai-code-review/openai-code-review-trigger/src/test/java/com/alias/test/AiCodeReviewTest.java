package com.alias.test;

import com.alias.domain.service.impl.ReviewPullRequestStreamingService;
import com.alias.infrastructure.git.GitCommand;
import com.alias.utils.VCSUtils;
import org.junit.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;

/**
 * Integration-style tests for {@link ReviewPullRequestStreamingService}
 * focusing on:
 * 1. PR review summary generation
 * 2. Single file review generation
 * <p>
 * The tests prepare minimal request data, invoke the corresponding
 * internal methods via reflection, and log the AI (mocked) results.
 */
public class AiCodeReviewTest {

    /**
     * 测试 PR Review Summary 生成逻辑
     * <p>
     * 准备一个简单的 FileChanges 对象，调用 ReviewPullRequestStreamingService
     * 中的 generatePrSummary 方法，并打印返回的 JSON 字符串。
     */
    @Test
    public void test_reviewSummary() throws Exception {
        String apiKey = "";

        OpenAiApi openAiApi = new OpenAiApi(apiKey);
        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi);
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        // GitCommand 在本测试中不会被真正使用，可以构造一个空实现
        GitCommand gitCommand = new GitCommand((String) null);

        ReviewPullRequestStreamingService service = new ReviewPullRequestStreamingService(gitCommand, chatClient);

        // 构造一个简单的 FileChanges 作为输入
        VCSUtils.FileChanges fileChanges = new VCSUtils.FileChanges("src/main/java/com/alias/demo/DemoClass.java", null);
        fileChanges.linesChanged = 3;
        fileChanges.changes.add(VCSUtils.Change.add(10, "System.out.println(\"hello\");"));

        String ragContext = "Mock RAG context for review summary.";
        int maxPromptChars = 180_000;

        // 通过反射调用 private String generatePrSummary(...)
        Method generatePrSummary = ReviewPullRequestStreamingService.class.getDeclaredMethod(
                "generatePrSummary", VCSUtils.FileChanges.class, String.class, int.class
        );
        generatePrSummary.setAccessible(true);

        String resultJson = (String) generatePrSummary.invoke(
                service, fileChanges, ragContext, maxPromptChars
        );

        System.out.println("========= Review Summary Result =========");
        System.out.println(resultJson);
        System.out.println("=========================================");
    }

    /**
     * 测试单文件 Review 逻辑
     * <p>
     * 准备一个简单的 FileChanges 对象，调用 ReviewPullRequestStreamingService
     * 中的 reviewSingleFileStreaming 方法，并打印返回的 JSON 字符串。
     */
    @Test
    public void test_singleFileReview() throws Exception {
        String apiKey = "";

        OpenAiApi openAiApi = new OpenAiApi(apiKey);
        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi);
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        GitCommand gitCommand = new GitCommand((String) null);

        ReviewPullRequestStreamingService service = new ReviewPullRequestStreamingService(gitCommand, chatClient);

        // 构造一个简单的 FileChanges 作为输入
        VCSUtils.FileChanges fileChanges = new VCSUtils.FileChanges("src/main/java/com/alias/demo/DemoClass.java", null);
        fileChanges.linesChanged = 5;
        fileChanges.changes.add(VCSUtils.Change.add(10, "System.out.println(\"hello\");"));
        fileChanges.changes.add(VCSUtils.Change.delete(8, "int x = 1;"));

        String ragContext = "Mock RAG context for single file review.";
        int maxPromptChars = 180_000;

        // reviewSingleFileStreaming 的 SseEmitter 参数当前实现中未真正使用，传一个空的 emitter 即可
        SseEmitter emitter = new SseEmitter();

        // 通过反射调用 private String reviewSingleFileStreaming(...)
        Method reviewSingleFileStreaming = ReviewPullRequestStreamingService.class.getDeclaredMethod(
                "reviewSingleFileStreaming", VCSUtils.FileChanges.class, String.class, int.class, SseEmitter.class
        );
        reviewSingleFileStreaming.setAccessible(true);

        String resultJson = (String) reviewSingleFileStreaming.invoke(
                service, fileChanges, ragContext, maxPromptChars, emitter
        );

        System.out.println("========= Single File Review Result =========");
        System.out.println(resultJson);
        System.out.println("=============================================");
    }
}

