package com.alias.domain.controller;

import com.alias.domain.model.ChatContext;
import com.alias.domain.model.ChatRequest;
import com.alias.domain.model.Response;
import com.alias.domain.service.IAiConversationService;
import org.springframework.ai.chat.model.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * AI Conversation Controller
 * Handles chat requests with context management and streaming
 */
@Slf4j
@Tag(name = "AI对话接口", description = "支持多轮对话、上下文管理和流式响应的AI对话接口")
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/ai-chat")
public class AiConversationController {

    @Autowired
    private IAiConversationService conversationService;

    /**
     * Send a chat message and get response
     *
     * @param request chat request
     * @return chat response
     */
    @Operation(summary = "发送聊天消息", description = "发送用户消息并获取AI响应，支持多轮对话")
    @PostMapping("/chat")
    public Response<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            // Validate request
            if (request == null || request.getMessage() == null || request.getMessage().isEmpty()) {
                return Response.<ChatResponse>builder().code("4000").info("Message is required").build();
            }

            if (request.getUserId() == null || request.getUserId().isEmpty()) {
                return Response.<ChatResponse>builder().code("4000").info("User ID is required").build();
            }

            // Set default conversation ID if not provided
            if (request.getConversationId() == null || request.getConversationId().isEmpty()) {
                request.setConversationId(UUID.randomUUID().toString());
            }

            log.info("Chat request received. conversationId={}, userId={}, messageLength={}", request.getConversationId(), request.getUserId(), request.getMessage().length());

            // Process chat
            ChatResponse response = conversationService.chat(request);

            log.info("Chat completed successfully. conversationId={}", request.getConversationId());

            return Response.<ChatResponse>builder().code("0000").info("Chat completed successfully").data(response).build();

        } catch (Exception e) {
            log.error("Chat failed. error={}", e.getMessage(), e);
            return Response.<ChatResponse>builder().code("5000").info("Chat failed: " + e.getMessage()).build();
        }
    }

    /**
     * Send a chat message with streaming response
     *
     * @param request chat request
     * @return SSE emitter for streaming
     */
    @Operation(summary = "流式聊天", description = "发送消息并以流式方式接收AI响应")
    @PostMapping("/chat-stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 minutes timeout

        try {
            // Validate request
            if (request == null || request.getMessage() == null || request.getMessage().isEmpty()) {
                emitter.send(SseEmitter.event().name("error").data("Message is required"));
                emitter.complete();
                return emitter;
            }

            if (request.getUserId() == null || request.getUserId().isEmpty()) {
                emitter.send(SseEmitter.event().name("error").data("User ID is required"));
                emitter.complete();
                return emitter;
            }

            // Set default conversation ID if not provided
            if (request.getConversationId() == null || request.getConversationId().isEmpty()) {
                request.setConversationId(UUID.randomUUID().toString());
            }

            log.info("Stream chat request received. conversationId={}, userId={}", request.getConversationId(), request.getUserId());

            // Process stream chat in a separate thread
            new Thread(() -> {
                try {
                    conversationService.chatStream(request, emitter);
                } catch (Exception e) {
                    log.error("Stream chat failed. error={}", e.getMessage(), e);
                    try {
                        emitter.send(SseEmitter.event().name("error").data("Stream chat failed: " + e.getMessage()));
                    } catch (Exception ex) {
                        log.error("Error sending error event", ex);
                    }
                }
            }).start();

        } catch (Exception e) {
            log.error("Stream chat setup failed. error={}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("Stream setup failed: " + e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                log.error("Error sending error event", ex);
            }
        }

        return emitter;
    }

    /**
     * Get conversation context
     *
     * @param conversationId conversation ID
     * @return conversation context
     */
    @Operation(summary = "获取对话上下文", description = "获取指定对话的完整上下文和历史记录")
    @GetMapping("/context/{conversationId}")
    public Response<ChatContext> getContext(@PathVariable("conversationId") String conversationId) {
        try {
            ChatContext context = conversationService.getConversationHistory(conversationId);

            if (context == null) {
                return Response.<ChatContext>builder().code("4004").info("Conversation not found").build();
            }

            log.info("Context retrieved. conversationId={}, messageCount={}", conversationId, context.getMessageCount());

            return Response.<ChatContext>builder().code("0000").info("Success").data(context).build();

        } catch (Exception e) {
            log.error("Failed to get context. conversationId={}, error={}", conversationId, e.getMessage(), e);
            return Response.<ChatContext>builder().code("5000").info("Failed to get context: " + e.getMessage()).build();
        }
    }

    /**
     * Clear conversation history
     *
     * @param conversationId conversation ID
     * @return response
     */
    @Operation(summary = "清空对话历史", description = "清空指定对话的所有消息历史")
    @DeleteMapping("/context/{conversationId}/clear")
    public Response<String> clearContext(@PathVariable("conversationId") String conversationId) {
        try {
            conversationService.clearConversation(conversationId);

            log.info("Conversation cleared. conversationId={}", conversationId);

            return Response.<String>builder().code("0000").info("Conversation cleared successfully").data(conversationId).build();

        } catch (Exception e) {
            log.error("Failed to clear context. conversationId={}, error={}", conversationId, e.getMessage(), e);
            return Response.<String>builder().code("5000").info("Failed to clear context: " + e.getMessage()).build();
        }
    }

    /**
     * Delete conversation
     *
     * @param conversationId conversation ID
     * @return response
     */
    @Operation(summary = "删除对话", description = "删除指定的对话及其所有历史记录")
    @DeleteMapping("/context/{conversationId}")
    public Response<String> deleteContext(@PathVariable("conversationId") String conversationId) {
        try {
            conversationService.deleteConversation(conversationId);

            log.info("Conversation deleted. conversationId={}", conversationId);

            return Response.<String>builder().code("0000").info("Conversation deleted successfully").data(conversationId).build();

        } catch (Exception e) {
            log.error("Failed to delete context. conversationId={}, error={}", conversationId, e.getMessage(), e);
            return Response.<String>builder().code("5000").info("Failed to delete context: " + e.getMessage()).build();
        }
    }
}
