package com.alias.domain.service;

import com.alias.domain.model.ChatContext;
import com.alias.domain.model.ChatRequest;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI Conversation Service Interface
 * Handles multi-turn conversations with context management and streaming
 */
public interface IAiConversationService {

    /**
     * Send a chat message and get response
     *
     * @param request chat request
     * @return chat response
     * @throws Exception if chat fails
     */
    ChatResponse chat(ChatRequest request) throws Exception;

    /**
     * Send a chat message with streaming response
     *
     * @param request chat request
     * @param emitter SSE emitter for streaming
     * @throws Exception if chat fails
     */
    void chatStream(ChatRequest request, SseEmitter emitter) throws Exception;

    /**
     * Get or create conversation context
     *
     * @param conversationId conversation ID (null to create new)
     * @param userId         user ID
     * @return chat context
     */
    ChatContext getOrCreateContext(String conversationId, String userId);

    /**
     * Get conversation context
     *
     * @param conversationId conversation ID
     * @return chat context or null if not found
     */
    ChatContext getContext(String conversationId);

    /**
     * Save conversation context
     *
     * @param context chat context
     */
    void saveContext(ChatContext context);

    /**
     * Clear conversation history
     *
     * @param conversationId conversation ID
     */
    void clearConversation(String conversationId);

    /**
     * Delete conversation
     *
     * @param conversationId conversation ID
     */
    void deleteConversation(String conversationId);

    /**
     * Get conversation history
     *
     * @param conversationId conversation ID
     * @return chat context with history
     */
    ChatContext getConversationHistory(String conversationId);
}
