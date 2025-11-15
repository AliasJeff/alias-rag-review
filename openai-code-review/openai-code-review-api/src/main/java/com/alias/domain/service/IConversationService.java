package com.alias.domain.service;

import com.alias.domain.model.Conversation;

import java.util.List;
import java.util.UUID;

/**
 * Conversation Service Interface
 * Manages conversation operations
 */
public interface IConversationService {

    /**
     * Create a new conversation
     *
     * @param conversation the conversation to create
     * @return the created conversation
     */
    Conversation createConversation(Conversation conversation);

    /**
     * Get conversation by ID
     *
     * @param conversationId the conversation ID
     * @return the conversation or null if not found
     */
    Conversation getConversationById(UUID conversationId);

    /**
     * Get all conversations for a client user
     *
     * @param clientIdentifier the client identifier
     * @return list of conversations
     */
    List<Conversation> getConversationsByClientIdentifier(UUID clientIdentifier);

    /**
     * Update conversation
     *
     * @param conversation the conversation to update
     * @return the updated conversation
     */
    Conversation updateConversation(Conversation conversation);

    /**
     * Update conversation status
     *
     * @param conversationId the conversation ID
     * @param status         the new status
     */
    void updateConversationStatus(UUID conversationId, String status);

    /**
     * Delete conversation
     *
     * @param conversationId the conversation ID
     */
    void deleteConversation(UUID conversationId);

    /**
     * Get conversations by PR URL
     *
     * @param prUrl the PR URL
     * @return list of conversations
     */
    List<Conversation> getConversationsByPrUrl(String prUrl);
}
