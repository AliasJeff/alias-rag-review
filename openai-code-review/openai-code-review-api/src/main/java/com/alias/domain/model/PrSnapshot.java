package com.alias.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * PR Snapshot Model
 * Caches PR file diffs and content for conversations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Snapshot ID (UUID)
     */
    private UUID id;

    /**
     * PR URL (unique identifier for a PR snapshot)
     */
    private String url;

    /**
     * Client identifier (references client_users.client_identifier)
     */
    private UUID clientIdentifier;

    /**
     * Repository name
     */
    private String repoName;

    /**
     * Pull request number
     */
    private Integer prNumber;

    /**
     * Branch name
     */
    private String branch;

    /**
     * File changes payload (JSONB)
     */
    private Map<String, Object> fileChanges;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Update timestamp
     */
    private LocalDateTime updatedAt;
}
