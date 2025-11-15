package com.alias.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Custom Chat Response model
 * Wraps the AI response with metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Response content from AI
     */
    private String content;

    /**
     * Conversation ID
     */
    private String conversationId;

    /**
     * Message ID (for the assistant's response)
     */
    private String messageId;

    /**
     * Total message count in conversation
     */
    private Integer messageCount;

    /**
     * Tokens used in this response
     */
    private Integer tokensUsed;

    /**
     * Total tokens used in conversation
     */
    private Integer totalTokens;

    /**
     * Model used for this response
     */
    private String model;

    /**
     * Response timestamp
     */
    private LocalDateTime timestamp;

    /**
     * Response status (success, error, etc.)
     */
    private String status;

    /**
     * Error message if any
     */
    private String errorMessage;
}
