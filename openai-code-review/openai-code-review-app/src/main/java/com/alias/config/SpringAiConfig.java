package com.alias.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Configuration
 * Configures the ChatClient bean for OpenAI integration
 */
@Configuration
public class SpringAiConfig {

    /**
     * Create ChatClient bean for OpenAI
     * The OpenAiChatModel is autoconfigured by Spring AI starter
     *
     * @param chatModel the OpenAI chat model
     * @return configured ChatClient
     */
    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
