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
     * Conversation ID (references conversations.id)
     */
    private UUID conversationId;

    /**
     * File path in the PR
     */
    private String filePath;

    /**
     * Git diff content
     */
    private String diff;

    /**
     * File content before changes
     */
    private String contentBefore;

    /**
     * File content after changes
     */
    private String contentAfter;

    /**
     * Additional metadata as JSON
     */
    private Map<String, Object> metadata;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;
}
