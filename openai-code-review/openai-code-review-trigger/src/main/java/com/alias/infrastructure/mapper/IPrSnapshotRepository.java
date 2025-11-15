package com.alias.infrastructure.mapper;

import com.alias.domain.model.PrSnapshot;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PR Snapshot Repository Interface
 * Data access layer for PR snapshots
 */
@Mapper
public interface IPrSnapshotRepository {

    /**
     * Save or update a PR snapshot
     *
     * @param snapshot the snapshot to save
     * @return the saved snapshot
     */
    PrSnapshot save(PrSnapshot snapshot);

    /**
     * Find snapshot by ID
     *
     * @param id the snapshot ID
     * @return optional containing the snapshot
     */
    Optional<PrSnapshot> findById(UUID id);

    /**
     * Find all snapshots for a conversation
     *
     * @param conversationId the conversation ID
     * @return list of snapshots
     */
    List<PrSnapshot> findByConversationId(UUID conversationId);

    /**
     * Find snapshot by conversation ID and file path
     *
     * @param conversationId the conversation ID
     * @param filePath       the file path
     * @return optional containing the snapshot
     */
    Optional<PrSnapshot> findByConversationIdAndFilePath(UUID conversationId, String filePath);

    /**
     * Find snapshots by file path pattern (using LIKE)
     *
     * @param conversationId  the conversation ID
     * @param filePathPattern the file path pattern
     * @return list of matching snapshots
     */
    List<PrSnapshot> findByConversationIdAndFilePathLike(UUID conversationId, String filePathPattern);

    /**
     * Delete snapshot by ID
     *
     * @param id the snapshot ID
     */
    void deleteById(UUID id);

    /**
     * Delete all snapshots for a conversation
     *
     * @param conversationId the conversation ID
     */
    void deleteByConversationId(UUID conversationId);

    /**
     * Count snapshots for a conversation
     *
     * @param conversationId the conversation ID
     * @return the count
     */
    long countByConversationId(UUID conversationId);
}
