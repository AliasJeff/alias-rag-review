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
 * Conversation Model
 * Represents a conversation session associated with a client user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Conversation ID (UUID)
     */
    private UUID id;

    /**
     * Client identifier (references client_users.client_identifier)
     */
    private UUID clientIdentifier;

    /**
     * Conversation title
     */
    private String title;

    /**
     * PR URL
     */
    private String prUrl;

    /**
     * Repository name
     */
    private String repo;

    /**
     * PR number
     */
    private Integer prNumber;

    /**
     * Conversation status: active/closed/archived/error
     */
    private String status;

    /**
     * Additional metadata as JSON
     */
    private Map<String, Object> metadata;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;
}
