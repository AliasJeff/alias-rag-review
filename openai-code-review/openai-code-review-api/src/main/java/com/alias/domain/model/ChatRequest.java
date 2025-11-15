package com.alias.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Chat request model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Conversation ID (optional, creates new if not provided)
     */
    private String conversationId;

    /**
     * User ID
     */
    private String userId;

    /**
     * User message
     */
    private String message;

    /**
     * System prompt (optional, overrides context system prompt)
     */
    private String systemPrompt;

    /**
     * Model to use (optional, uses default if not provided)
     */
    private String model;

    /**
     * Temperature (optional)
     */
    private Double temperature;

    /**
     * Maximum tokens (optional)
     */
    private Integer maxTokens;

    /**
     * Enable streaming
     */
    @Builder.Default
    private Boolean stream = false;

    /**
     * Number of previous messages to include in context
     */
    @Builder.Default
    private Integer contextSize = 10;
}
