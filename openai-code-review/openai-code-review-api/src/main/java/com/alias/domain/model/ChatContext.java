package com.alias.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat context model - maintains conversation history and metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Conversation ID
     */
    private String conversationId;

    /**
     * User ID
     */
    private String userId;

    /**
     * Conversation title
     */
    private String title;

    /**
     * System prompt/instructions
     */
    private String systemPrompt;

    /**
     * Message history
     */
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * Model to use
     */
    @Builder.Default
    private String model = "gpt-4o";

    /**
     * Temperature for response generation
     */
    @Builder.Default
    private Double temperature = 0.7;

    /**
     * Maximum tokens for response
     */
    @Builder.Default
    private Integer maxTokens = 2000;

    /**
     * Total tokens used in conversation
     */
    @Builder.Default
    private Integer totalTokens = 0;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Add message to context
     */
    public void addMessage(ChatMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        message.setIndex(this.messages.size());
        message.setConversationId(this.conversationId);
        this.messages.add(message);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Get last N messages for API call
     */
    public List<ChatMessage> getLastNMessages(int n) {
        if (this.messages == null || this.messages.isEmpty()) {
            return new ArrayList<>();
        }
        int startIndex = Math.max(0, this.messages.size() - n);
        return new ArrayList<>(this.messages.subList(startIndex, this.messages.size()));
    }

    /**
     * Clear message history
     */
    public void clearMessages() {
        if (this.messages != null) {
            this.messages.clear();
        }
    }

    /**
     * Get conversation length
     */
    public int getMessageCount() {
        return this.messages == null ? 0 : this.messages.size();
    }
}
