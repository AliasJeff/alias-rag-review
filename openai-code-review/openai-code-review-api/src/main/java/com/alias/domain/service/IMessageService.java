package com.alias.domain.service;

import com.alias.domain.model.Message;

import java.util.List;
import java.util.UUID;

/**
 * Message Service Interface
 * Manages message operations
 */
public interface IMessageService {

    /**
     * Create a new message
     *
     * @param message the message to create
     * @return the created message
     */
    Message createMessage(Message message);

    /**
     * Get message by ID
     *
     * @param messageId the message ID
     * @return the message or null if not found
     */
    Message getMessageById(UUID messageId);

    /**
     * Get all messages for a conversation
     *
     * @param conversationId the conversation ID
     * @return list of messages
     */
    List<Message> getMessagesByConversationId(UUID conversationId);

    /**
     * Get messages for a conversation with pagination
     *
     * @param conversationId the conversation ID
     * @param limit          the maximum number of messages to return
     * @param offset         the offset for pagination
     * @return list of messages
     */
    List<Message> getMessagesByConversationId(UUID conversationId, int limit, int offset);

    /**
     * Delete message
     *
     * @param messageId the message ID
     */
    void deleteMessage(UUID messageId);

    /**
     * Delete all messages for a conversation
     *
     * @param conversationId the conversation ID
     */
    void deleteMessagesByConversationId(UUID conversationId);

    /**
     * Get message count for a conversation
     *
     * @param conversationId the conversation ID
     * @return the message count
     */
    long getMessageCount(UUID conversationId);
}
