package com.alias.infrastructure.mapper;

import com.alias.domain.model.Conversation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Conversation Repository Interface
 * Data access layer for conversations
 */
@Mapper
public interface IConversationRepository {

    /**
     * Save or update a conversation
     *
     * @param conversation the conversation to save
     * @return the saved conversation
     */
    Conversation save(Conversation conversation);

    /**
     * Find conversation by ID
     *
     * @param id the conversation ID
     * @return optional containing the conversation
     */
    Optional<Conversation> findById(UUID id);

    /**
     * Find all conversations for a client identifier
     *
     * @param clientIdentifier the client identifier
     * @return list of conversations
     */
    List<Conversation> findByClientIdentifier(UUID clientIdentifier);

    /**
     * Find conversations by PR URL
     *
     * @param prUrl the PR URL
     * @return list of conversations
     */
    List<Conversation> findByPrUrl(String prUrl);

    /**
     * Find conversations by status
     *
     * @param status the conversation status
     * @return list of conversations
     */
    List<Conversation> findByStatus(String status);

    /**
     * Delete conversation by ID
     *
     * @param id the conversation ID
     */
    void deleteById(UUID id);

    /**
     * Delete all conversations for a client identifier
     *
     * @param clientIdentifier the client identifier
     */
    void deleteByClientIdentifier(UUID clientIdentifier);

    /**
     * Count conversations for a client identifier
     *
     * @param clientIdentifier the client identifier
     * @return the count
     */
    long countByClientIdentifier(UUID clientIdentifier);
}
