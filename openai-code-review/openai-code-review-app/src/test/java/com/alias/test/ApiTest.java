package com.alias.test;

import com.alias.OpenAiCodeReview;
import org.junit.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import com.alias.infrastructure.openai.IOpenAI;
import com.alias.domain.model.ChatRequest;
import com.alias.domain.model.ModelEnum;
import com.alias.domain.service.IAiConversationService;
import com.alias.domain.service.impl.AiConversationService;
import com.alias.domain.service.impl.ReviewPullRequestService;
import com.alias.infrastructure.openai.impl.OpenAI;
import com.alias.infrastructure.git.GitCommand;
import com.alias.config.AppConfig;
import org.springframework.ai.chat.model.ChatResponse;


public class ApiTest {

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
                cfg.getString("openai", "apiHost"), cfg.getString("openai", "apiKey"));

        // 执行 PR 代码审查
        ReviewPullRequestService reviewPullRequestService = new ReviewPullRequestService(gitCommand, openAI);
        reviewPullRequestService.setModel(ModelEnum.GPT_4O);
        reviewPullRequestService.exec(url);

        System.out.println("AI Code review success!");
    }

    /**
     * 测试一次性对话 - 单轮对话
     * 直接调用 OpenAI API 进行一次性对话
     */
    @Test
    public void test_singleTurnConversation() throws Exception {
        System.out.println("========== 测试一次性对话 ==========");

        // 启动 Spring 应用上下文以获取 ChatClient bean
        ApplicationContext context = SpringApplication.run(OpenAiCodeReview.class);
        ChatClient chatClient = context.getBean(ChatClient.class);

        // 创建会话服务
        IAiConversationService conversationService = new AiConversationService(chatClient);

        // 构建聊天请求
        ChatRequest request = ChatRequest.builder().userId("test-user-001").message("请用中文简要解释什么是人工智能？").systemPrompt("你是一个友好的AI助手，用简洁清晰的语言回答问题。").stream(false).build();

        System.out.println("发送请求: " + request.getMessage());

        // 发送请求并获取响应
        ChatResponse response = conversationService.chat(request);

        System.out.println("对话ID: " + request.getConversationId());
        System.out.println("AI 响应: " + response.getResult().getOutput().getText());
        System.out.println("模型: gpt-4o");
        System.out.println("完成原因: " + response.getResult().getMetadata().getFinishReason());
        System.out.println("========== 一次性对话完成 ==========\n");
    }

    /**
     * 测试多轮对话 - 保持上下文
     * 演示如何进行多轮对话并保持对话历史
     */
    @Test
    public void test_multiTurnConversation() throws Exception {
        System.out.println("========== 测试多轮对话 ==========");

        // 启动 Spring 应用上下文以获取 ChatClient bean
        ApplicationContext context = SpringApplication.run(OpenAiCodeReview.class);
        ChatClient chatClient = context.getBean(ChatClient.class);

        // 创建会话服务
        IAiConversationService conversationService = new AiConversationService(chatClient);

        String conversationId = java.util.UUID.randomUUID().toString();
        String userId = "test-user-002";

        // 第一轮对话
        System.out.println("--- 第一轮对话 ---");
        ChatRequest request1 = ChatRequest.builder().conversationId(conversationId).userId(userId).message("Java中的List和Array有什么区别？").systemPrompt("你是一个Java专家，用专业但易懂的方式解答问题。").stream(false).build();

        System.out.println("用户问: " + request1.getMessage());
        ChatResponse response1 = conversationService.chat(request1);
        System.out.println("AI 答: " + response1.getResult().getOutput().getText());

        // 第二轮对话 - 基于第一轮的上下文
        System.out.println("\n--- 第二轮对话 ---");
        ChatRequest request2 = ChatRequest.builder().conversationId(conversationId).userId(userId).message("我刚才问了什么？").stream(false).build();

        System.out.println("用户问: " + request2.getMessage());
        ChatResponse response2 = conversationService.chat(request2);
        System.out.println("AI 答: " + response2.getResult().getOutput().getText());

        // 第三轮对话 - 继续上下文
        System.out.println("\n--- 第三轮对话 ---");
        ChatRequest request3 = ChatRequest.builder().conversationId(conversationId).userId(userId).message("能给一个实际的代码例子吗？").stream(false).build();

        System.out.println("用户问: " + request3.getMessage());
        ChatResponse response3 = conversationService.chat(request3);
        System.out.println("AI 答: " + response3.getResult().getOutput().getText());

        System.out.println("\n========== 多轮对话完成 ==========\n");
    }

    /**
     * 测试流式传输对话
     * 演示如何使用 Server-Sent Events (SSE) 进行流式响应
     */
    @Test
    public void test_streamingConversation() throws Exception {
        System.out.println("========== 测试流式传输对话 ==========");

        // 启动 Spring 应用上下文以获取 ChatClient bean
        ApplicationContext context = SpringApplication.run(OpenAiCodeReview.class);
        ChatClient chatClient = context.getBean(ChatClient.class);

        // 创建会话服务
        IAiConversationService conversationService = new AiConversationService(chatClient);

        // 构建流式聊天请求
        ChatRequest request = ChatRequest.builder().userId("test-user-003").message("请详细解释什么是微服务架构，包括它的优点和缺点。").systemPrompt("你是一个软件架构师，用详细的方式解释概念。").stream(true).build();

        System.out.println("发送流式请求: " + request.getMessage());
        System.out.println("开始接收流式响应...\n");

        // 创建 SSE 模拟器来接收流式数据
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();

        // 在后台线程中处理流式响应
        Thread streamThread = new Thread(() -> {
            try {
                conversationService.chatStream(request, emitter);
            } catch (Exception e) {
                System.err.println("流式传输错误: " + e.getMessage());
                e.printStackTrace();
            }
        });

        streamThread.start();

        // 等待流式传输完成
        streamThread.join();

        System.out.println("\n========== 流式传输对话完成 ==========\n");
    }

    /**
     * 测试使用 Spring AI ChatClient 直接调用 OpenAI
     * 这是最低级别的 API 调用方式
     */
    @Test
    public void test_directChatClientCall() throws Exception {
        System.out.println("========== 测试直接 ChatClient 调用 ==========");

        // 启动 Spring 应用上下文以获取 ChatClient bean
        ApplicationContext context = SpringApplication.run(OpenAiCodeReview.class);
        ChatClient chatClient = context.getBean(ChatClient.class);

        // 构建提示词
        Prompt prompt = new Prompt(
                new UserMessage("用中文解释什么是 REST API"), OpenAiChatOptions.builder().model("gpt-4o").build()
        );

        System.out.println("发送请求到 ChatClient");

        // 调用 ChatClient - 使用 prompt().call().entity() 链式调用
        org.springframework.ai.chat.model.ChatResponse response = chatClient.prompt(prompt).call().entity(org.springframework.ai.chat.model.ChatResponse.class);

        String content = response.getResult().getOutput().getText();
        System.out.println("ChatClient 响应: " + content);
        System.out.println("========== 直接调用完成 ==========\n");
    }

}
