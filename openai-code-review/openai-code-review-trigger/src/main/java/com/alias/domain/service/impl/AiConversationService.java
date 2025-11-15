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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * In-memory context storage (replace with database in production)
     */
    private final ConcurrentHashMap<String, ChatContext> contextStore = new ConcurrentHashMap<>();

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

        // Build prompt for Spring AI
        String userMessageContent = request.getMessage();
        Prompt prompt = new Prompt(
                new UserMessage(userMessageContent), OpenAiChatOptions.builder().model(request.getModel() != null ? request.getModel() : context.getModel()).build()
        );

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

            // Build messages for API call
            List<ChatCompletionRequestDTO.Prompt> messages = buildPrompts(context, request);

            // Prepare request
            ChatCompletionRequestDTO chatRequest = new ChatCompletionRequestDTO();
            chatRequest.setModel(request.getModel() != null ? request.getModel() : context.getModel());
            chatRequest.setMessages(messages);

            // Send initial message
            emitter.send(SseEmitter.event().id(context.getConversationId()).name("start").data("Streaming started"));

            // Call OpenAI API with streaming
            StringBuilder fullResponse = new StringBuilder();
            streamChatCompletion(chatRequest, emitter, fullResponse);

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
        if (conversationId != null && contextStore.containsKey(conversationId)) {
            return contextStore.get(conversationId);
        }

        String newConversationId = conversationId != null ? conversationId : UUID.randomUUID().toString();
        ChatContext context = ChatContext.builder().conversationId(newConversationId).userId(userId).title("Conversation " + newConversationId.substring(0, 8)).model("gpt-4o").temperature(0.7).maxTokens(2000).totalTokens(0).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        contextStore.put(newConversationId, context);
        logger.info("Created new context. conversationId={}, userId={}", newConversationId, userId);

        return context;
    }

    @Override
    public ChatContext getContext(String conversationId) {
        return contextStore.get(conversationId);
    }

    @Override
    public void saveContext(ChatContext context) {
        context.setUpdatedAt(LocalDateTime.now());
        contextStore.put(context.getConversationId(), context);
        logger.debug("Context saved. conversationId={}, messageCount={}", context.getConversationId(), context.getMessageCount());
    }

    @Override
    public void clearConversation(String conversationId) {
        ChatContext context = contextStore.get(conversationId);
        if (context != null) {
            context.clearMessages();
            saveContext(context);
            logger.info("Conversation cleared. conversationId={}", conversationId);
        }
    }

    @Override
    public void deleteConversation(String conversationId) {
        contextStore.remove(conversationId);
        logger.info("Conversation deleted. conversationId={}", conversationId);
    }

    @Override
    public ChatContext getConversationHistory(String conversationId) {
        return contextStore.get(conversationId);
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
     */
    private void streamChatCompletion(ChatCompletionRequestDTO chatRequest, SseEmitter emitter, StringBuilder fullResponse) throws IOException {
        try {
            // Extract user message
            String userMessage = chatRequest.getMessages().stream().filter(msg -> "user".equals(msg.getRole())).map(ChatCompletionRequestDTO.Prompt::getContent).findFirst().orElse("");

            // Build prompt
            Prompt prompt = new Prompt(
                    new UserMessage(userMessage), OpenAiChatOptions.builder().model(chatRequest.getModel()).build()
            );

            // Call chat client and stream response
            String content = chatClient.prompt(prompt).call().chatResponse().getResult().getOutput().getText();

            // Send content in chunks (simulate streaming)
            String[] words = content.split("\\s+");
            for (String word : words) {
                fullResponse.append(word).append(" ");
                emitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).name("message").data(word + " "));

                // Small delay to simulate streaming
                Thread.sleep(10);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Streaming interrupted", e);
            throw new IOException("Streaming interrupted", e);
        }
    }
}
