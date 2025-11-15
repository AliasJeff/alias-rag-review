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
 * Message Model
 * Represents a message within a conversation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Message ID (UUID)
     */
    private UUID id;

    /**
     * Conversation ID (references conversations.id)
     */
    private UUID conversationId;

    /**
     * Message role: user/assistant/system
     */
    private String role;

    /**
     * Message type: text/code/analysis
     */
    private String type;

    /**
     * Message content
     */
    private String content;

    /**
     * Additional metadata as JSON
     */
    private Map<String, Object> metadata;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;
}
