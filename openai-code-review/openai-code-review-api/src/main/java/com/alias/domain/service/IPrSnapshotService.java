package com.alias.domain.service;

import com.alias.domain.model.PrSnapshot;

import java.util.List;
import java.util.UUID;

/**
 * PR Snapshot Service Interface
 * Manages PR snapshot operations
 */
public interface IPrSnapshotService {

    /**
     * Create a new PR snapshot
     *
     * @param snapshot the snapshot to create
     * @return the created snapshot
     */
    PrSnapshot createSnapshot(PrSnapshot snapshot);

    /**
     * Get snapshot by ID
     *
     * @param snapshotId the snapshot ID
     * @return the snapshot or null if not found
     */
    PrSnapshot getSnapshotById(UUID snapshotId);

    /**
     * Get all snapshots for a conversation
     *
     * @param conversationId the conversation ID
     * @return list of snapshots
     */
    List<PrSnapshot> getSnapshotsByConversationId(UUID conversationId);

    /**
     * Get snapshot by conversation ID and file path
     *
     * @param conversationId the conversation ID
     * @param filePath       the file path
     * @return the snapshot or null if not found
     */
    PrSnapshot getSnapshotByConversationAndFilePath(UUID conversationId, String filePath);

    /**
     * Update snapshot
     *
     * @param snapshot the snapshot to update
     * @return the updated snapshot
     */
    PrSnapshot updateSnapshot(PrSnapshot snapshot);

    /**
     * Delete snapshot
     *
     * @param snapshotId the snapshot ID
     */
    void deleteSnapshot(UUID snapshotId);

    /**
     * Delete all snapshots for a conversation
     *
     * @param conversationId the conversation ID
     */
    void deleteSnapshotsByConversationId(UUID conversationId);

    /**
     * Search snapshots by file path pattern
     *
     * @param conversationId  the conversation ID
     * @param filePathPattern the file path pattern
     * @return list of matching snapshots
     */
    List<PrSnapshot> searchSnapshotsByFilePath(UUID conversationId, String filePathPattern);
}
