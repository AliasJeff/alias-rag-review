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
     * Get all snapshots for a client
     *
     * @param clientIdentifier the client identifier
     * @return list of snapshots
     */
    List<PrSnapshot> getSnapshotsByClientIdentifier(UUID clientIdentifier);

    /**
     * Get snapshot by PR URL
     *
     * @param url the PR url
     * @return the snapshot or null if not found
     */
    PrSnapshot getSnapshotByUrl(String url);

    /**
     * Get snapshots by repository and PR number
     *
     * @param repoName the repository name
     * @param prNumber the PR number
     * @return list of snapshots
     */
    List<PrSnapshot> getSnapshotsByRepoNameAndPrNumber(String repoName, Integer prNumber);

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
     * Delete all snapshots for a client
     *
     * @param clientIdentifier the client identifier
     */
    void deleteSnapshotsByClientIdentifier(UUID clientIdentifier);

    /**
     * Search snapshots by keyword (repo name / branch / url)
     *
     * @param clientIdentifier the client identifier
     * @param keyword          the keyword for fuzzy search
     * @return list of matching snapshots
     */
    List<PrSnapshot> searchSnapshots(UUID clientIdentifier, String keyword);
}
