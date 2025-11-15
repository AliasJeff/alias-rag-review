package com.alias.infrastructure.openai.impl;

import com.alias.infrastructure.openai.IOpenAI;
import com.alias.infrastructure.openai.dto.ChatCompletionRequestDTO;
import com.alias.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI implementation using Spring AI ChatClient
 * Replaces the custom HTTP-based implementation with Spring AI abstraction
 */
public class OpenAI implements IOpenAI {

    private static final Logger logger = LoggerFactory.getLogger(OpenAI.class);

    private final ChatClient chatClient;

    /**
     * Constructor for Spring AI-based OpenAI client
     *
     * @param chatClient the Spring AI ChatClient bean
     */
    public OpenAI(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Legacy constructor for backward compatibility
     * This constructor is deprecated; use OpenAI(ChatClient) instead
     *
     * @param apiHost API host (ignored, use Spring Boot configuration)
     * @param apiKey  API key (ignored, use Spring Boot configuration)
     * @deprecated Use OpenAI(ChatClient) constructor instead
     */
    @Deprecated
    public OpenAI(String apiHost, String apiKey) {
        logger.warn("Using deprecated OpenAI constructor with apiHost and apiKey. " + "Please use OpenAI(ChatClient) and configure via Spring Boot properties.");
        this.chatClient = null;
    }

    @Override
    public ChatCompletionSyncResponseDTO completions(ChatCompletionRequestDTO requestDTO) throws Exception {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not initialized. " + "Please use OpenAI(ChatClient) constructor and ensure Spring AI is properly configured.");
        }

        try {
            // Extract the user message from the request
            String userMessage = requestDTO.getMessages().stream().filter(msg -> "user".equals(msg.getRole())).map(ChatCompletionRequestDTO.Prompt::getContent).findFirst().orElseThrow(() -> new IllegalArgumentException("No user message found in request"));

            logger.debug("Sending chat completion request. model={}, messageLength={}", requestDTO.getModel(), userMessage.length());

            // Build the prompt with the user message
            Prompt prompt = new Prompt(
                    new UserMessage(userMessage), OpenAiChatOptions.builder().model(requestDTO.getModel()).build()
            );

            // Call the chat client
            ChatResponse response = chatClient.prompt(prompt).call().entity(ChatResponse.class);

            // Convert the response to the expected DTO format
            ChatCompletionSyncResponseDTO responseDTO = new ChatCompletionSyncResponseDTO();
            ChatCompletionSyncResponseDTO.Choice choice = new ChatCompletionSyncResponseDTO.Choice();
            ChatCompletionSyncResponseDTO.Message message = new ChatCompletionSyncResponseDTO.Message();

            message.setRole("assistant");
            message.setContent(response.getResult().getOutput().getText());

            choice.setMessage(message);
            List<ChatCompletionSyncResponseDTO.Choice> choices = new ArrayList<>();
            choices.add(choice);
            responseDTO.setChoices(choices);

            logger.debug("Received chat completion response. contentLength={}", message.getContent().length());

            return responseDTO;

        } catch (Exception e) {
            logger.error("OpenAI API request failed", e);
            throw new RuntimeException("OpenAI API request failed: " + e.getMessage(), e);
        }
    }
}
