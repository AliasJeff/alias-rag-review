package com.alias.domain.controller;

import com.alias.config.AppConfig;
import com.alias.domain.model.*;
import com.alias.domain.service.IAiConversationService;
import com.alias.domain.service.IPrSnapshotService;
import com.alias.domain.service.impl.ReviewPullRequestStreamingService;
import com.alias.domain.utils.ChatUtils;
import com.alias.infrastructure.git.GitCommand;
import com.alias.utils.GitHubPrUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    @Resource
    private IAiConversationService aiConversationService;

    @Resource
    private ChatClient chatClient;

    @Resource
    private IPrSnapshotService prSnapshotService;


    @PostConstruct
    public void init() {
        // Initialize ChatUtils with AI conversation service for intent detection
        ChatUtils.setAiConversationService(aiConversationService);
        log.info("ChatUtils initialized with AI conversation service for AI-based intent detection");
    }

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
            ChatResponse response = aiConversationService.chat(request);

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

        SseEmitter emitter = new SseEmitter(0L);

        try {
            // Validate request
            if (request.getMessage() == null || request.getMessage().isEmpty()) {
                String errorMsg = "### ❌ 错误\n\n消息内容不能为空。\n\n";
                emitter.send(SseEmitter.event().name("error").data(buildEmitterPayload(errorMsg, request != null ? request.getConversationId() : null)));
                emitter.complete();
                return emitter;
            }

            if (request.getUserId() == null || request.getUserId().isEmpty()) {
                String errorMsg = "### ❌ 错误\n\n用户 ID 不能为空。\n\n";
                emitter.send(SseEmitter.event().name("error").data(buildEmitterPayload(errorMsg, request.getConversationId())));
                emitter.complete();
                return emitter;
            }

            // Set default conversation ID if not provided
            if (request.getConversationId() == null || request.getConversationId().isEmpty()) {
                request.setConversationId(UUID.randomUUID().toString());
            }

            log.info("Stream chat request received. conversationId={}, userId={}", request.getConversationId(), request.getUserId());

            // Use final variable for lambda
            final ChatRequest requestForThread = request;

            // Use CompletableFuture instead of new Thread
            CompletableFuture.runAsync(() -> {
                try {
                    aiConversationService.chatStream(requestForThread, emitter);
                } catch (Exception e) {
                    log.error("Stream chat failed", e);
                    try {
                        String errorMsg = "### ❌ Streaming Chat Failed\n\n" + "**Error Message:**\n" + "```\n" + e.getMessage() + "\n" + "```\n\n";
                        emitter.send(SseEmitter.event().name("error").data(buildEmitterPayload(errorMsg, requestForThread.getConversationId())));
                    } catch (Exception ex) {
                        log.error("Error sending error event", ex);
                    } finally {
                        emitter.complete();
                    }
                }
            });

            // Optional: timeout handling
            emitter.onTimeout(() -> {
                log.warn("SSE emitter timeout for conversationId={}", request.getConversationId());
                emitter.complete();
            });

            emitter.onCompletion(() -> log.info("SSE emitter completed for conversationId={}", request.getConversationId()));

        } catch (Exception e) {
            log.error("Stream chat setup failed", e);
            try {
                String errorMsg = "### ❌ Initialization Failed\n\n" + "**Error Message:**\n" + "```\n" + e.getMessage() + "\n" + "```\n\n";
                emitter.send(SseEmitter.event().name("error").data(buildEmitterPayload(errorMsg, request.getConversationId())));
            } catch (Exception ex) {
                log.error("Error sending error event", ex);
            } finally {
                emitter.complete();
            }
        }

        return emitter;
    }


    /**
     * Streaming chat with automatic intent detection and RAG routing
     * <p>
     * Automatically detects user intent:
     * 1. CODE_REVIEW: User asks for code review or mentions PR/diff → uses RAG context
     * 2. REVIEW_FOLLOWUP: User follows up on review results → uses RAG context
     * 3. GENERAL_CHAT: General questions → normal chat without RAG
     *
     * @param request chat request (should include repository field for RAG context)
     * @return SSE emitter for streaming
     */
    @Operation(summary = "流式聊天和RAG路由", description = "自动判断用户意图，支持代码Review和普通对话的流式SSE接口")
    @PostMapping("/chat-stream-router")
    public SseEmitter chatStreamWithRouter(@RequestBody ChatRequest request) {

        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout

        try {
            // Validate request
            if (request.getMessage() == null || request.getMessage().isEmpty()) {
                String errorMsg = "### ❌ Error\n\nMessage content cannot be empty.\n\n";
                emitter.send(SseEmitter.event().name("error").data(buildEmitterPayload(errorMsg, request != null ? request.getConversationId() : null)));
                emitter.complete();
                return emitter;
            }

            if (request.getUserId() == null || request.getUserId().isEmpty()) {
                String errorMsg = "### ❌ Error\n\nUser ID cannot be empty.\n\n";
                emitter.send(SseEmitter.event().name("error").data(buildEmitterPayload(errorMsg, request.getConversationId())));
                emitter.complete();
                return emitter;
            }

            // Set default conversation ID if not provided
            if (request.getConversationId() == null || request.getConversationId().isEmpty()) {
                request.setConversationId(UUID.randomUUID().toString());
            }

            log.info("Stream chat router request received. conversationId={}, userId={}", request.getConversationId(), request.getUserId());

            // Use final variable for lambda
            final ChatRequest requestForThread = request;

            // Use CompletableFuture instead of new Thread
            CompletableFuture.runAsync(() -> {
                try {
                    // Get conversation history for context
                    ChatContext context = aiConversationService.getConversationHistory(requestForThread.getConversationId());
                    String conversationHistory = context != null ? context.toString() : "";

                    // Detect user intent
                    ChatUtils.IntentType intent = ChatUtils.detectIntent(requestForThread.getMessage(), conversationHistory);
                    log.info("Detected intent: {} for conversationId={}", intent.getValue(), requestForThread.getConversationId());

                    // Route based on intent
                    if (intent == ChatUtils.IntentType.CODE_REVIEW || intent == ChatUtils.IntentType.REVIEW_FOLLOWUP) {
                        // Check if PR URL exists in conversation
                        String prUrl = null;
                        // Extract PR URL from user message, and save to conversation if not exists
                        try {
                            java.util.UUID conversationUuid = java.util.UUID.fromString(requestForThread.getConversationId());
                            Conversation conversation = aiConversationService.getConversationById(conversationUuid);

                            if (conversation != null) {
                                prUrl = conversation.getPrUrl();
                            }

                            // If no PR URL in conversation, try to extract from user message
                            if ((prUrl == null || prUrl.isEmpty()) && requestForThread.getMessage() != null) {
                                String extractedPrUrl = extractPrUrlFromMessage(requestForThread.getMessage());
                                if (extractedPrUrl != null && !extractedPrUrl.isEmpty()) {
                                    prUrl = extractedPrUrl;
                                    // Save PR URL to conversation
                                    if (conversation == null) {
                                        // Create new conversation if not exists
                                        conversation = new Conversation();
                                        conversation.setId(conversationUuid);
                                        conversation.setClientIdentifier(java.util.UUID.fromString(requestForThread.getUserId()));
                                        conversation.setTitle("Code Review: " + prUrl);
                                        conversation.setPrUrl(prUrl);
                                        conversation.setStatus("active");
                                        aiConversationService.createConversation(conversation);
                                        log.info("Created new conversation with PR URL. conversationId={}, prUrl={}", conversationUuid, prUrl);
                                    } else if (conversation.getPrUrl() == null || conversation.getPrUrl().isEmpty()) {
                                        conversation.setPrUrl(prUrl);
                                        aiConversationService.updateConversation(conversation);
                                        log.info("Updated conversation with PR URL. conversationId={}, prUrl={}", conversationUuid, prUrl);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to retrieve/update PR URL from conversation. conversationId={}, error={}", requestForThread.getConversationId(), e.getMessage());
                        }

                        // If no PR URL found, send message to user
                        if (prUrl == null || prUrl.isEmpty()) {
                            log.info("No PR URL found for review intent. conversationId={}", requestForThread.getConversationId());
                            try {
                                String message = "### ⚠️ Missing PR URL\n\n" + "To provide accurate code review, please provide a GitHub Pull Request URL.\n\n" + "**Example Format:**\n" + "```\n" + "https://github.com/owner/repo/pull/123\n" + "```\n\n";
                                emitter.send(SseEmitter.event().name("message").data(buildEmitterPayload(message, requestForThread.getConversationId())));
                                emitter.send(SseEmitter.event().name("complete").data("Streaming completed"));
                                emitter.complete();
                                return;
                            } catch (IOException e) {
                                log.error("Failed to send PR URL missing message", e);
                                emitter.complete();
                                return;
                            }
                        }

                        if (intent == ChatUtils.IntentType.CODE_REVIEW) {
                            // Start code review
                            try {
                                log.info("Starting code review for PR. conversationId={}, prUrl={}", requestForThread.getConversationId(), prUrl);

                                // Send review start event
                                String reviewStartMsg = "## 🔍 Starting Code Review\n\n" + "**PR URL:** " + prUrl + "\n\n" + "Analyzing code changes...\n\n";
                                emitter.send(SseEmitter.event().name("review_start").data(buildEmitterPayload(reviewStartMsg, requestForThread.getConversationId())));

                                // Get GitHub token from config
                                String githubToken = AppConfig.getInstance().requireString("github", "token");

                                // Create GitCommand and ReviewPullRequestStreamingService
                                GitCommand gitCommand = new GitCommand(githubToken);
                                ReviewPullRequestStreamingService reviewService = new ReviewPullRequestStreamingService(gitCommand, chatClient, prSnapshotService);
                                reviewService.setConversationId(requestForThread.getConversationId());
                                reviewService.setClientIdentifier(parseUuidQuietly(requestForThread.getUserId()));

                                // Parse PR URL and set parameters
                                GitHubPrUtils.PrInfo prInfo = GitHubPrUtils.parsePrUrl(prUrl);
                                reviewService.setRepository(prInfo.repository);
                                reviewService.setPrNumber(prInfo.prNumber);
                                reviewService.setPrUrl(prUrl);

                                // Execute streaming review
                                reviewService.execStreaming(emitter);

                                log.info("Code review completed. conversationId={}, prUrl={}", requestForThread.getConversationId(), prUrl);
                            } catch (Exception reviewErr) {
                                log.error("Code review failed. conversationId={}, prUrl={}, error={}", requestForThread.getConversationId(), prUrl, reviewErr.getMessage(), reviewErr);
                                try {
                                    String errorMsg = "### ❌ Code Review Failed\n\n" + "**Error Message:**\n" + "```\n" + reviewErr.getMessage() + "\n" + "```\n\n";
                                    emitter.send(SseEmitter.event().name("error").data(buildEmitterPayload(errorMsg, requestForThread.getConversationId())));
                                } catch (IOException ioErr) {
                                    log.error("Failed to send error event", ioErr);
                                }
                                emitter.complete();
                            }
                            return;
                        } else {
                            // Get RAG context from conversation PR URL
                            String ragContext = "";
                            try {
                                // Extract repository from PR URL
                                GitHubPrUtils.PrInfo prInfo = GitHubPrUtils.parsePrUrl(prUrl);
                                String repository = prInfo.repository;

                                if (repository != null && !repository.isEmpty()) {
                                    ragContext = ChatUtils.getRagContext(requestForThread.getMessage(), repository);
                                    log.info("Retrieved RAG context from PR URL. repository={}, contextSize={}", repository, ragContext.length());
                                }
                            } catch (Exception e) {
                                log.warn("Failed to extract repository from PR URL. prUrl={}, error={}", prUrl, e.getMessage());
                            }

                            // Enhance system prompt with RAG context if available
                            if (!ragContext.isEmpty()) {
                                String enhancedSystemPrompt = buildEnhancedSystemPrompt(requestForThread.getSystemPrompt(), ragContext);
                                requestForThread.setSystemPrompt(enhancedSystemPrompt);
                                log.debug("Enhanced system prompt with RAG context");
                            }
                        }
                    }

                    // Stream the response using standard chat stream
                    aiConversationService.chatStream(requestForThread, emitter);

                } catch (Exception e) {
                    log.error("Stream chat router failed", e);
                    try {
                        String errorMsg = "### ❌ Smart Routing Failed\n\n" + "**Error Message:**\n" + "```\n" + e.getMessage() + "\n" + "```\n\n";
                        emitter.send(SseEmitter.event().name("error").data(buildEmitterPayload(errorMsg, requestForThread.getConversationId())));
                    } catch (Exception ex) {
                        log.error("Error sending error event", ex);
                    } finally {
                        emitter.complete();
                    }
                }
            });

            // Optional: timeout handling
            emitter.onTimeout(() -> {
                log.warn("SSE emitter timeout for conversationId={}", request.getConversationId());
                emitter.complete();
            });

            emitter.onCompletion(() -> log.info("SSE emitter completed for conversationId={}", request.getConversationId()));

        } catch (Exception e) {
            log.error("Stream chat router setup failed", e);
            try {
                String errorMsg = "### ❌ Initialization Failed\n\n" + "**Error Message:**\n" + "```\n" + e.getMessage() + "\n" + "```\n\n";
                emitter.send(SseEmitter.event().name("error").data(buildEmitterPayload(errorMsg, request.getConversationId())));
            } catch (Exception ex) {
                log.error("Error sending error event", ex);
            } finally {
                emitter.complete();
            }
        }

        return emitter;
    }

    /**
     * Extract repository information from request
     * Can be from systemPrompt, conversationId, or a dedicated field
     *
     * @param request chat request
     * @return repository string in format owner/repo, or null if not found
     */
    private String extractRepositoryFromRequest(ChatRequest request) {
        // Try to extract from system prompt if it contains repository info
        if (request.getSystemPrompt() != null && request.getSystemPrompt().contains("repository:")) {
            String[] parts = request.getSystemPrompt().split("repository:");
            if (parts.length > 1) {
                String repo = parts[1].trim().split("\n")[0].trim();
                if (!repo.isEmpty()) {
                    return repo;
                }
            }
        }

        // Could also be extracted from conversation context or metadata
        // For now, return null if not found
        return null;
    }

    private UUID parseUuidQuietly(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for client identifier: {}", raw);
            return null;
        }
    }

    /**
     * Build enhanced system prompt with RAG context
     *
     * @param originalPrompt original system prompt
     * @param ragContext     RAG context from code repository
     * @return enhanced system prompt
     */
    private String buildEnhancedSystemPrompt(String originalPrompt, String ragContext) {
        StringBuilder enhanced = new StringBuilder();

        if (originalPrompt != null && !originalPrompt.isEmpty()) {
            enhanced.append(originalPrompt).append("\n\n");
        }

        enhanced.append("=== Code Context from Repository ===\n");
        enhanced.append(ragContext).append("\n");
        enhanced.append("=== End of Code Context ===\n\n");
        enhanced.append("Please use the above code context to provide more accurate and relevant responses.");

        return enhanced.toString();
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
            ChatContext context = aiConversationService.getConversationHistory(conversationId);

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
            aiConversationService.clearConversation(conversationId);

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
            aiConversationService.deleteConversation(conversationId);

            log.info("Conversation deleted. conversationId={}", conversationId);

            return Response.<String>builder().code("0000").info("Conversation deleted successfully").data(conversationId).build();

        } catch (Exception e) {
            log.error("Failed to delete context. conversationId={}, error={}", conversationId, e.getMessage(), e);
            return Response.<String>builder().code("5000").info("Failed to delete context: " + e.getMessage()).build();
        }
    }

    /**
     * Extract PR URL from user message using regex pattern
     * Supports GitHub PR URLs in format: https://github.com/{owner}/{repo}/pull/{number}
     *
     * @param message user message
     * @return extracted PR URL or null if not found
     */
    private String extractPrUrlFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        // Regex pattern to match GitHub PR URLs
        // Matches: https://github.com/{owner}/{repo}/pull/{number}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "https://github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+/pull/\\d+"
        );
        java.util.regex.Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            String prUrl = matcher.group();
            log.debug("Extracted PR URL from message: {}", prUrl);
            return prUrl;
        }

        return null;
    }

    /**
     * Escape JSON string
     *
     * @param input input string
     * @return escaped JSON string
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String buildEmitterPayload(String content, String conversationId) {
        String safeContent = content != null ? content : "";
        String safeConversationId = conversationId != null ? conversationId : "";
        return "{\"content\":\"" + escapeJson(safeContent) + "\",\"conversationId\":\"" + escapeJson(safeConversationId) + "\"}";
    }
}
