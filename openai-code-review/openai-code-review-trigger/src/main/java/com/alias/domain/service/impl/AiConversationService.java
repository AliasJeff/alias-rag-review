package com.alias.domain.service.impl;

import com.alias.domain.model.*;
import com.alias.domain.service.IAiConversationService;
import com.alias.infrastructure.openai.dto.ChatCompletionRequestDTO;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * AI Conversation Service Implementation
 * Manages multi-turn conversations with context and streaming support
 */
@Service
public class AiConversationService implements IAiConversationService {

    private static final Logger logger = LoggerFactory.getLogger(AiConversationService.class);

    private final ChatClient chatClient;

    @Resource
    private MessageService messageService;

    @Resource
    private ConversationService conversationService;

    public AiConversationService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws Exception {
        logger.info("Processing chat request. conversationId={}, userId={}, messageLength={}", request.getConversationId(), request.getUserId(), request.getMessage() != null ? request.getMessage().length() : 0);

        // Get or create context
        ChatContext context = getOrCreateContext(request.getConversationId(), request.getUserId());

        // Set system prompt if provided
        if (request.getSystemPrompt() != null) {
            context.setSystemPrompt(request.getSystemPrompt());
        }

        // Add user message to context
        ChatMessage userMessage = ChatMessage.builder().id(UUID.randomUUID().toString()).role("user").content(request.getMessage()).createdAt(LocalDateTime.now()).build();
        context.addMessage(userMessage);

        // Build prompt with context using buildPrompts
        List<ChatCompletionRequestDTO.Prompt> prompts = buildPrompts(context, request);
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (ChatCompletionRequestDTO.Prompt p : prompts) {
            if ("system".equals(p.getRole())) {
                messages.add(new org.springframework.ai.chat.messages.SystemMessage(p.getContent()));
            } else if ("user".equals(p.getRole())) {
                messages.add(new org.springframework.ai.chat.messages.UserMessage(p.getContent()));
            } else if ("assistant".equals(p.getRole())) {
                messages.add(new org.springframework.ai.chat.messages.AssistantMessage(p.getContent()));
            }
        }

        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder().model(request.getModel() != null ? request.getModel() : context.getModel()).build());

        // Call Spring AI ChatClient
        org.springframework.ai.chat.model.ChatResponse springResponse = chatClient.prompt(prompt).call().chatResponse();

        // Extract response content
        String assistantContent = springResponse.getResult().getOutput().getText();
        String messageId = UUID.randomUUID().toString();

        // Add assistant message to context
        ChatMessage assistantMessage = ChatMessage.builder().id(messageId).role("assistant").content(assistantContent).createdAt(LocalDateTime.now()).build();
        context.addMessage(assistantMessage);

        // Save context
        saveContext(context);

        // Save messages to database
        try {
            UUID conversationId = UUID.fromString(context.getConversationId());

            // Save user message to database
            Message userMsg = Message.builder().conversationId(conversationId).role("user").type("text").content(request.getMessage()).build();
            messageService.createMessage(userMsg);
            logger.debug("User message saved to database. conversationId={}", conversationId);

            // Save assistant message to database
            Message assistantMsg = Message.builder().conversationId(conversationId).role("assistant").type("text").content(assistantContent).build();
            messageService.createMessage(assistantMsg);
            logger.debug("Assistant message saved to database. conversationId={}", conversationId);

            // Update conversation status if needed
            Conversation conversation = conversationService.getConversationById(conversationId);
            if (conversation != null && "error".equals(conversation.getStatus())) {
                conversation.setStatus("active");
                conversationService.updateConversation(conversation);
                logger.debug("Conversation status updated to active. conversationId={}", conversationId);
            }

        } catch (Exception e) {
            logger.error("Failed to save messages to database. conversationId={}, error={}", context.getConversationId(), e.getMessage(), e);
            // Don't throw exception, just log it to avoid breaking the chat flow
        }

        logger.info("Chat completed. conversationId={}, responseLength={}", context.getConversationId(), assistantContent.length());

        // Build and return custom ChatResponse
        return ChatResponse.builder().content(assistantContent).conversationId(context.getConversationId()).messageId(messageId).messageCount(context.getMessageCount()).model(context.getModel()).timestamp(LocalDateTime.now()).status("success").build();
    }

    @Override
    public void chatStream(ChatRequest request, SseEmitter emitter) throws Exception {
        logger.info("Processing streaming chat request. conversationId={}, userId={}", request.getConversationId(), request.getUserId());

        try {
            // Get or create context
            ChatContext context = getOrCreateContext(request.getConversationId(), request.getUserId());

            // Set system prompt if provided
            if (request.getSystemPrompt() != null) {
                context.setSystemPrompt(request.getSystemPrompt());
            }

            // Add user message to context
            ChatMessage userMessage = ChatMessage.builder().id(UUID.randomUUID().toString()).role("user").content(request.getMessage()).createdAt(LocalDateTime.now()).build();
            context.addMessage(userMessage);

            // Build prompt with context using buildPrompts
            List<ChatCompletionRequestDTO.Prompt> prompts = buildPrompts(context, request);
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            for (ChatCompletionRequestDTO.Prompt p : prompts) {
                if ("system".equals(p.getRole())) {
                    messages.add(new org.springframework.ai.chat.messages.SystemMessage(p.getContent()));
                } else if ("user".equals(p.getRole())) {
                    messages.add(new org.springframework.ai.chat.messages.UserMessage(p.getContent()));
                } else if ("assistant".equals(p.getRole())) {
                    messages.add(new org.springframework.ai.chat.messages.AssistantMessage(p.getContent()));
                }
            }

            Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder().model(request.getModel() != null ? request.getModel() : context.getModel()).build());

            // Call Spring AI ChatClient stream method
            StringBuilder fullResponse = new StringBuilder();
            chatClient.prompt(prompt).stream().content().doOnNext(chunk -> {
                try {
                    fullResponse.append(chunk);
                    emitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).name("message").data(buildEmitterPayload(chunk, context.getConversationId())));
                } catch (IOException e) {
                    logger.error("Error sending stream chunk", e);
                }
            }).blockLast();

            // Add assistant message to context
            ChatMessage assistantMessage = ChatMessage.builder().id(UUID.randomUUID().toString()).role("assistant").content(fullResponse.toString()).createdAt(LocalDateTime.now()).build();
            context.addMessage(assistantMessage);

            // Save context
            saveContext(context);

            // Save messages to database
            try {
                UUID conversationId = UUID.fromString(context.getConversationId());

                // Save user message to database
                Message userMsg = Message.builder().conversationId(conversationId).role("user").type("text").content(request.getMessage()).build();
                messageService.createMessage(userMsg);
                logger.debug("User message saved to database. conversationId={}", conversationId);

                // Save assistant message to database
                Message assistantMsg = Message.builder().conversationId(conversationId).role("assistant").type("text").content(fullResponse.toString()).build();
                messageService.createMessage(assistantMsg);
                logger.debug("Assistant message saved to database. conversationId={}", conversationId);

                // Update conversation status if needed
                Conversation conversation = conversationService.getConversationById(conversationId);
                if (conversation != null && "error".equals(conversation.getStatus())) {
                    conversation.setStatus("active");
                    conversationService.updateConversation(conversation);
                    logger.debug("Conversation status updated to active. conversationId={}", conversationId);
                }

            } catch (Exception e) {
                logger.error("Failed to save messages to database. conversationId={}, error={}", context.getConversationId(), e.getMessage(), e);
                // Don't throw exception, just log it to avoid breaking the stream
            }

            // Send completion event
            emitter.send(SseEmitter.event().id(context.getConversationId()).name("complete").data("Streaming completed"));

            emitter.complete();

            logger.info("Streaming chat completed. conversationId={}, responseLength={}", context.getConversationId(), fullResponse.length());

        } catch (IOException e) {
            logger.error("Streaming error", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("Error sending error event", ex);
            }
        } catch (Exception e) {
            logger.error("Unexpected error in streaming", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("Error sending error event", ex);
            }
        }
    }

    @Override
    public ChatContext getOrCreateContext(String conversationId, String userId) {
        String effectiveConversationId = conversationId != null ? conversationId : UUID.randomUUID().toString();

        ChatContext.ChatContextBuilder builder = ChatContext.builder().conversationId(effectiveConversationId).userId(userId).model("gpt-4o").temperature(0.7).maxTokens(2000).totalTokens(0);

        List<ChatMessage> chatMessages = new ArrayList<>();

        try {
            UUID conversationUuid = UUID.fromString(effectiveConversationId);

            // Load conversation metadata
            Conversation conversation = conversationService.getConversationById(conversationUuid);
            if (conversation != null) {
                builder.title(conversation.getTitle());
                builder.createdAt(conversation.getCreatedAt());
                builder.updatedAt(conversation.getUpdatedAt());
            } else {
                builder.title("Conversation " + effectiveConversationId.substring(0, 8));
                builder.createdAt(LocalDateTime.now());
                builder.updatedAt(LocalDateTime.now());
            }

            // Load existing messages as context
            List<Message> dbMessages = messageService.getMessagesByConversationId(conversationUuid);
            for (Message m : dbMessages) {
                ChatMessage chatMessage = ChatMessage.builder().id(m.getId() != null ? m.getId().toString() : null).conversationId(effectiveConversationId).role(m.getRole()).content(m.getContent()).createdAt(m.getCreatedAt()).build();
                chatMessages.add(chatMessage);
            }

            builder.messages(chatMessages);

            logger.info("Loaded context from database. conversationId={}, userId={}, messageCount={}", effectiveConversationId, userId, chatMessages.size());

        } catch (IllegalArgumentException e) {
            // Invalid UUID or no database records, fall back to a new in-memory context
            builder.title("Conversation " + effectiveConversationId.substring(0, 8));
            builder.createdAt(LocalDateTime.now());
            builder.updatedAt(LocalDateTime.now());
            builder.messages(new ArrayList<>());
            logger.warn("Invalid conversationId format, creating new context in memory. conversationId={}, userId={}", effectiveConversationId, userId);
        } catch (Exception e) {
            // Any database error should not break chat flow
            builder.title("Conversation " + effectiveConversationId.substring(0, 8));
            builder.createdAt(LocalDateTime.now());
            builder.updatedAt(LocalDateTime.now());
            builder.messages(new ArrayList<>());
            logger.error("Failed to load context from database. conversationId={}, error={}", effectiveConversationId, e.getMessage(), e);
        }

        return builder.build();
    }

    @Override
    public ChatContext getContext(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        return getOrCreateContext(conversationId, null);
    }

    @Override
    public void saveContext(ChatContext context) {
        // Context is now derived from database messages and conversation metadata.
        // Saving is handled by MessageService and ConversationService, so this is a no-op
        // kept for interface compatibility and logging.
        if (context != null) {
            context.setUpdatedAt(LocalDateTime.now());
            logger.debug("Context save requested (no-op). conversationId={}, messageCount={}", context.getConversationId(), context.getMessageCount());
        }
    }

    @Override
    public void clearConversation(String conversationId) {
        if (conversationId == null) {
            return;
        }

        try {
            UUID conversationUuid = UUID.fromString(conversationId);
            messageService.deleteMessagesByConversationId(conversationUuid);
            logger.info("Conversation messages cleared in database. conversationId={}", conversationId);
        } catch (Exception e) {
            logger.error("Failed to clear conversation messages. conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    @Override
    public void deleteConversation(String conversationId) {
        if (conversationId == null) {
            return;
        }

        try {
            UUID conversationUuid = UUID.fromString(conversationId);
            messageService.deleteMessagesByConversationId(conversationUuid);
            conversationService.deleteConversation(conversationUuid);
            logger.info("Conversation and messages deleted from database. conversationId={}", conversationId);
        } catch (Exception e) {
            logger.error("Failed to delete conversation. conversationId={}, error={}", conversationId, e.getMessage(), e);
        }
    }

    @Override
    public ChatContext getConversationHistory(String conversationId) {
        return getContext(conversationId);
    }

    /**
     * Build prompts for API call from context
     */
    private List<ChatCompletionRequestDTO.Prompt> buildPrompts(ChatContext context, ChatRequest request) {
        List<ChatCompletionRequestDTO.Prompt> prompts = new ArrayList<>();

        // Add system prompt if provided
        String systemPrompt = request.getSystemPrompt() != null ? request.getSystemPrompt() : context.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            prompts.add(new ChatCompletionRequestDTO.Prompt("system", systemPrompt));
        }

        // Add context messages (limited by contextSize)
        int contextSize = request.getContextSize() != null ? request.getContextSize() : 10;
        List<ChatMessage> contextMessages = context.getLastNMessages(contextSize);
        for (ChatMessage msg : contextMessages) {
            prompts.add(new ChatCompletionRequestDTO.Prompt(msg.getRole(), msg.getContent()));
        }

        return prompts;
    }

    /**
     * Stream chat completion using Spring AI ChatClient
     * Sends 2-6 words/characters at a time to simulate natural streaming
     */
    private void streamChatCompletion(ChatCompletionRequestDTO chatRequest, SseEmitter emitter, String conversationId, ChatContext context, StringBuilder fullResponse) throws IOException {
        try {
            // 取最新用户消息（messages 列表最后一条 role=user 的消息）
            String userMessage = chatRequest.getMessages().stream().filter(msg -> "user".equals(msg.getRole())).reduce((first, second) -> second) // 取最后一个
                    .map(ChatCompletionRequestDTO.Prompt::getContent).orElse("");

            // 将最新用户消息加入上下文
            ChatMessage userChatMessage = ChatMessage.builder().id(UUID.randomUUID().toString()).role("user").content(userMessage).createdAt(LocalDateTime.now()).build();
            context.addMessage(userChatMessage);

            // 构建上下文内容字符串
            StringBuilder contextText = new StringBuilder();
            if (context.getSystemPrompt() != null) {
                contextText.append("System: ").append(context.getSystemPrompt()).append("\n");
            }

            for (ChatMessage msg : context.getMessages()) {
                if ("user".equals(msg.getRole())) {
                    contextText.append("User: ").append(msg.getContent()).append("\n");
                } else if ("assistant".equals(msg.getRole())) {
                    contextText.append("Assistant: ").append(msg.getContent()).append("\n");
                }
            }

            // 追加最新用户消息
            contextText.append("User: ").append(userMessage).append("\n");

            // 创建单个 Prompt
            Prompt prompt = new Prompt(new UserMessage(contextText.toString()), OpenAiChatOptions.builder().model(chatRequest.getModel()).build());

            // 调用 chatClient
            String content = chatClient.prompt(prompt).call().chatResponse().getResult().getOutput().getText();

            // 按 token 拆分（保留中英文空格/换行）
            List<String> tokens = tokenizeContent(content);
            Random random = new Random();
            int i = 0;
            while (i < tokens.size()) {
                int chunkSize = random.nextInt(5) + 2;
                int endIndex = Math.min(i + chunkSize, tokens.size());

                StringBuilder chunk = new StringBuilder();
                for (int j = i; j < endIndex; j++) {
                    chunk.append(tokens.get(j));
                }

                String chunkContent = chunk.toString();
                fullResponse.append(chunkContent);

                // SSE 发送当前块
                emitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).name("message").data(buildEmitterPayload(chunkContent, conversationId)));

                Thread.sleep(50);
                i = endIndex;
            }

            // 将 AI 回复加入上下文
            ChatMessage assistantMessage = ChatMessage.builder().id(UUID.randomUUID().toString()).role("assistant").content(fullResponse.toString()).createdAt(LocalDateTime.now()).build();
            context.addMessage(assistantMessage);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Streaming interrupted", e);
            throw new IOException("Streaming interrupted", e);
        }
    }

    /**
     * Tokenize content into words (for English) or characters (for Chinese)
     * Mixed content is handled by splitting on spaces and treating Chinese characters individually
     */
    private List<String> tokenizeContent(String content) {
        List<String> tokens = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return tokens;
        }

        // Split by spaces first to separate words
        String[] parts = content.split("(?<=\\s)|(?=\\s)");

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            // Check if part contains Chinese characters
            if (containsChineseCharacters(part)) {
                // Split Chinese text into individual characters
                for (char c : part.toCharArray()) {
                    tokens.add(String.valueOf(c));
                }
            } else {
                // Keep English words as is
                tokens.add(part);
            }
        }

        return tokens;
    }

    /**
     * Check if string contains Chinese characters
     */
    private boolean containsChineseCharacters(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        for (char c : str.toCharArray()) {
            // Unicode range for CJK Unified Ideographs
            if ((c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF) || (c >= 0x20000 && c <= 0x2A6DF) || (c >= 0x2A700 && c <= 0x2B73F) || (c >= 0x2B740 && c <= 0x2B81F) || (c >= 0x2B820 && c <= 0x2CEAF) || (c >= 0xF900 && c <= 0xFAFF) || (c >= 0x2F800 && c <= 0x2FA1F)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Escape JSON string
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

    @Override
    public Conversation getConversationById(UUID conversationId) {
        if (conversationId == null) {
            return null;
        }
        return conversationService.getConversationById(conversationId);
    }

    @Override
    public Conversation createConversation(Conversation conversation) {
        logger.info("Creating conversation via AI service. conversationId={}", conversation.getId());
        return conversationService.createConversation(conversation);
    }

    @Override
    public Conversation updateConversation(Conversation conversation) {
        logger.info("Updating conversation via AI service. conversationId={}", conversation.getId());
        return conversationService.updateConversation(conversation);
    }
}
