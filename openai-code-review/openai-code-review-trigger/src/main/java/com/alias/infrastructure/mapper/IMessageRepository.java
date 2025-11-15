package com.alias.infrastructure.mapper;

import com.alias.domain.model.Message;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Message Repository Interface
 * Data access layer for messages
 */
@Mapper
public interface IMessageRepository {

    /**
     * Save or update a message
     *
     * @param message the message to save
     * @return the saved message
     */
    Message save(Message message);

    /**
     * Find message by ID
     *
     * @param id the message ID
     * @return optional containing the message
     */
    Optional<Message> findById(UUID id);

    /**
     * Find all messages for a conversation
     *
     * @param conversationId the conversation ID
     * @return list of messages
     */
    List<Message> findByConversationId(UUID conversationId);

    /**
     * Find messages for a conversation with limit and offset
     *
     * @param conversationId the conversation ID
     * @param limit          the maximum number of messages
     * @param offset         the offset for pagination
     * @return list of messages
     */
    List<Message> findByConversationId(UUID conversationId, int limit, int offset);

    /**
     * Find messages by conversation ID and role
     *
     * @param conversationId the conversation ID
     * @param role           the message role
     * @return list of messages
     */
    List<Message> findByConversationIdAndRole(UUID conversationId, String role);

    /**
     * Delete message by ID
     *
     * @param id the message ID
     */
    void deleteById(UUID id);

    /**
     * Delete all messages for a conversation
     *
     * @param conversationId the conversation ID
     */
    void deleteByConversationId(UUID conversationId);

    /**
     * Count messages for a conversation
     *
     * @param conversationId the conversation ID
     * @return the count
     */
    long countByConversationId(UUID conversationId);
}
