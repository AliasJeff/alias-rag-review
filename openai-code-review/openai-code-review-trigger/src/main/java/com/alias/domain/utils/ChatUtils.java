package com.alias.domain.utils;

import com.alias.config.AppConfig;
import com.alias.domain.model.ChatRequest;
import com.alias.domain.model.ChatResponse;
import com.alias.domain.service.IAiConversationService;
import com.alias.utils.IoUtils;
import com.alias.utils.ReviewJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chat utility class for intent detection and RAG context retrieval
 */
public class ChatUtils {

    private static final Logger logger = LoggerFactory.getLogger(ChatUtils.class);

    /**
     * -- SETTER --
     * Set the AI conversation service for intent detection
     * Should be called during application initialization
     *
     * @param service the AI conversation service instance
     */
    @Setter
    private static IAiConversationService aiConversationService;

    /**
     * Intent types for routing
     */
    @Getter
    public enum IntentType {
        CODE_REVIEW("code_review"), REVIEW_FOLLOWUP("review_followup"), GENERAL_CHAT("general_chat");

        private final String value;

        IntentType(String value) {
            this.value = value;
        }

    }

    /**
     * Detect user intent from message using AI
     * - CODE_REVIEW: user asks for code review or mentions PR/diff
     * - REVIEW_FOLLOWUP: user follows up on previous review results
     * - GENERAL_CHAT: general questions or discussions
     *
     * @param message             user message
     * @param conversationHistory conversation history (optional, for context)
     * @return detected intent type
     */
    public static IntentType detectIntent(String message, String conversationHistory) {
        if (message == null || message.isEmpty()) {
            return IntentType.GENERAL_CHAT;
        }

        try {
            // Use AI to detect intent if OpenAI client is available
            return detectIntentWithAI(message, conversationHistory);
        } catch (Exception e) {
            logger.warn("Failed to detect intent with AI, falling back to keyword matching. error={}", e.getMessage());
        }

        return detectIntentWithKeywords(message, conversationHistory);
    }

    /**
     * Detect intent using AI via aiConversationService
     *
     * @param message             user message
     * @param conversationHistory conversation history
     * @return detected intent type
     * @throws Exception if AI call fails
     */
    private static IntentType detectIntentWithAI(String message, String conversationHistory) throws Exception {
        if (aiConversationService == null) {
            logger.warn("AI conversation service not initialized, falling back to keyword matching");
            throw new RuntimeException("AI conversation service not available");
        }

        String systemPrompt = "You are an intent classifier. Analyze the user message and conversation history to determine the user's intent.\n\n" + "Classify the intent into one of these categories:\n" + "1. CODE_REVIEW: User asks for code review, mentions PR, diff, or wants to analyze code quality, security, performance, bugs, etc.\n" + "2. REVIEW_FOLLOWUP: User is following up on a previous code review, asking clarification questions like 'why', 'explain', 'how', or referring to previous suggestions.\n" + "3. GENERAL_CHAT: General questions or discussions not related to code review.\n\n" + "Respond with ONLY the intent type (CODE_REVIEW, REVIEW_FOLLOWUP, or GENERAL_CHAT), nothing else.";

        String userPrompt = "Conversation history:\n" + (conversationHistory != null && !conversationHistory.isEmpty() ? conversationHistory : "No previous conversation") + "\n\nCurrent message: " + message;

        ChatRequest request = ChatRequest.builder().conversationId(UUID.randomUUID().toString()).userId("system-intent-detector").message(userPrompt).systemPrompt(systemPrompt).model("gpt-4o").build();

        logger.debug("Calling AI to detect intent via aiConversationService. messageLength={}", message.length());
        ChatResponse response = aiConversationService.chat(request);

        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            logger.warn("AI returned empty response for intent detection");
            return IntentType.GENERAL_CHAT;
        }

        String intentText = response.getContent().trim().toUpperCase();
        logger.debug("AI detected intent: {}", intentText);

        // Parse the AI response
        if (intentText.contains("CODE_REVIEW")) {
            return IntentType.CODE_REVIEW;
        } else if (intentText.contains("REVIEW_FOLLOWUP")) {
            return IntentType.REVIEW_FOLLOWUP;
        } else {
            return IntentType.GENERAL_CHAT;
        }
    }

    /**
     * Detect intent using keyword matching (fallback)
     *
     * @param message             user message
     * @param conversationHistory conversation history
     * @return detected intent type
     */
    private static IntentType detectIntentWithKeywords(String message, String conversationHistory) {
        String lowerMessage = message.toLowerCase();

        // Keywords for code review intent
        String[] reviewKeywords = {"review", "code review", "pr review", "pull request", "diff", "commit", "check", "analyze", "quality", "bug", "issue", "error", "security", "performance", "optimization", "refactor", "best practice"
        };

        // Keywords for review followup intent
        String[] followupKeywords = {"why", "explain", "how", "what about", "what if", "more detail", "can you", "could you", "should i", "is this", "that line", "that code", "that part", "that comment", "that suggestion"
        };

        // Check for code review keywords
        for (String keyword : reviewKeywords) {
            if (lowerMessage.contains(keyword)) {
                return IntentType.CODE_REVIEW;
            }
        }

        // Check for followup keywords and if there's conversation history
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            for (String keyword : followupKeywords) {
                if (lowerMessage.contains(keyword)) {
                    return IntentType.REVIEW_FOLLOWUP;
                }
            }
        }

        return IntentType.GENERAL_CHAT;
    }

    /**
     * Get RAG context for code review
     * Calls RAG API to retrieve relevant code context based on the message
     *
     * @param message    user message (code content or query)
     * @param repository repository name (owner/repo format)
     * @return RAG context string, empty string if failed or not configured
     */
    public static String getRagContext(String message, String repository) {
        if (message == null || message.isEmpty()) {
            logger.warn("Message is empty, cannot get RAG context");
            return "";
        }

        if (repository == null || repository.isEmpty()) {
            logger.warn("Repository is empty, cannot get RAG context");
            return "";
        }

        try {
            // Extract repo name from repository (format: owner/repo, extract repo part)
            String repoName = extractRepoName(repository);
            if (repoName == null || repoName.isEmpty()) {
                logger.warn("Cannot extract repoName from repository: {}", repository);
                return "";
            }

            // Get RAG service URL
            String ragBaseUrl = AppConfig.getInstance().getString("rag", "apiBaseUrl");
            if (ragBaseUrl == null || ragBaseUrl.isEmpty()) {
                logger.warn("RAG API base URL is not configured");
                return "";
            }

            // Build RAG API URL
            String apiUrl = ragBaseUrl + "/review-context";

            logger.info("Calling RAG API to get context. repoName={}, messageSize={}", repoName, message.length());

            // Build request body JSON
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("repoName", repoName);
            requestMap.put("code", message);
            String requestBody = mapper.writeValueAsString(requestMap);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000); // 10 seconds
            conn.setReadTimeout(30000); // 30 seconds

            // Send request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int httpCode = conn.getResponseCode();
            if (httpCode / 100 != 2) {
                String errMsg = IoUtils.readStreamSafely(conn.getErrorStream());
                logger.warn("RAG API call failed, code={}, err={}", httpCode, errMsg);
                return "";
            }

            // Parse response
            String responseBody = IoUtils.readStreamSafely(conn.getInputStream());
            JsonNode root = mapper.readTree(responseBody);

            // Check response code
            String responseCode = ReviewJsonUtils.safeText(root, "code");
            if (!"0000".equals(responseCode)) {
                String info = ReviewJsonUtils.safeText(root, "info");
                logger.warn("RAG API returned non-success code: {}, info: {}", responseCode, info);
                return "";
            }

            // Extract data field
            String context = ReviewJsonUtils.safeText(root, "data");
            if (context == null || context.isEmpty()) {
                logger.warn("RAG API returned empty context");
                return "";
            }

            logger.info("Successfully retrieved RAG context. contextSize={}", context.length());
            return context;

        } catch (Exception e) {
            logger.error("Failed to get RAG context. error={}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Extract repo name from repository string
     * Format: owner/repo, returns repo part
     *
     * @param repository repository string in format owner/repo
     * @return repo name
     */
    private static String extractRepoName(String repository) {
        if (repository == null || repository.isEmpty()) {
            return null;
        }
        int lastSlash = repository.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < repository.length() - 1) {
            return repository.substring(lastSlash + 1);
        }
        return repository;
    }
}
