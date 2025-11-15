package com.alias.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Chat message model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Message ID
     */
    private String id;

    /**
     * Conversation ID
     */
    private String conversationId;

    /**
     * Message role: user, assistant, system
     */
    private String role;

    /**
     * Message content
     */
    private String content;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Token count
     */
    private Integer tokenCount;

    /**
     * Message index in conversation
     */
    private Integer index;
}
