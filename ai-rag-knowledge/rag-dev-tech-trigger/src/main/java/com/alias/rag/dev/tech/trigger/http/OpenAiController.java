package com.alias.rag.dev.tech.trigger.http;

import com.alias.rag.dev.tech.api.IAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "OpenAI AI 接口", description = "基于 OpenAI API 的 AI 对话接口")
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/openai/")
public class OpenAiController implements IAiService {

    @Resource
    private OpenAiChatClient chatClient;
    @Resource
    private PgVectorStore pgVectorStore;

    @Operation(
            summary = "生成 AI 回复（同步）",
            description = "使用指定的 OpenAI 模型同步生成 AI 回复。该接口会等待模型生成完整回复后返回结果。"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功生成回复",
                    content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    @Override
    public ChatResponse generate(
            @Parameter(description = "OpenAI 模型名称，例如：gpt-4o、gpt-3.5-turbo", required = true, example = "gpt-4o")
            @RequestParam("model") String model,
            @Parameter(description = "用户输入的消息内容", required = true, example = "1+1等于多少？")
            @RequestParam("message") String message) {
        return chatClient.call(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

    @Operation(
            summary = "生成 AI 回复（流式）",
            description = "使用指定的 OpenAI 模型以流式方式生成 AI 回复。该接口会实时返回模型生成的文本片段，适用于需要实时显示回复的场景。"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功开始流式生成",
                    content = @Content(schema = @Schema(implementation = Flux.class))),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStream(
            @Parameter(description = "OpenAI 模型名称，例如：gpt-4o、gpt-3.5-turbo", required = true, example = "gpt-4o")
            @RequestParam("model") String model,
            @Parameter(description = "用户输入的消息内容", required = true, example = "1+1")
            @RequestParam("message") String message) {
        return chatClient.stream(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

    @Operation(
            summary = "基于 RAG 生成 AI 回复（流式）",
            description = "结合 RAG（检索增强生成）技术，从知识库中检索相关文档，然后使用指定的 OpenAI 模型以流式方式生成 AI 回复。该接口会先从向量数据库中检索与问题相关的文档，然后将这些文档作为上下文提供给模型。回复内容将使用中文。"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功开始流式生成",
                    content = @Content(schema = @Schema(implementation = Flux.class))),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @RequestMapping(value = "generate_stream_rag", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStreamRag(
            @Parameter(description = "OpenAI 模型名称，例如：gpt-4o、gpt-3.5-turbo", required = true, example = "gpt-4o")
            @RequestParam("model") String model,
            @Parameter(description = "RAG 知识库标签，用于筛选特定知识库的文档", required = true, example = "java-knowledge")
            @RequestParam("ragTag") String ragTag,
            @Parameter(description = "用户输入的问题", required = true, example = "什么是 Spring Boot？")
            @RequestParam("message") String message) {

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 指定文档搜索
        SearchRequest request = SearchRequest.query(message)
                .withTopK(5)
                .withFilterExpression("knowledge == '" + ragTag + "'");

        List<Document> documents = pgVectorStore.similaritySearch(request);
        String documentCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentCollectors));

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        return chatClient.stream(new Prompt(
                messages,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

}