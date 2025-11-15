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
 * Client User Model
 * Represents a browser-based user identified by UUID from localStorage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key UUID
     */
    private UUID id;

    /**
     * Client identifier UUID (generated from browser localStorage)
     */
    private UUID clientIdentifier;

    /**
     * Encrypted GitHub token
     */
    private String githubToken;

    /**
     * Encrypted OpenAI API key
     */
    private String openaiApiKey;

    /**
     * Flag indicating if tokens are encrypted
     */
    private Boolean encrypted;

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
