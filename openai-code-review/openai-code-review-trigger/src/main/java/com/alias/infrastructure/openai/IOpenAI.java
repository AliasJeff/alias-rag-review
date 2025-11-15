package com.alias.infrastructure.openai;

import com.alias.infrastructure.openai.dto.ChatCompletionRequestDTO;
import com.alias.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;

/**
 * OpenAI API interface
 * Abstracts the OpenAI chat completion API
 */
public interface IOpenAI {

    /**
     * Call OpenAI chat completion API
     *
     * @param requestDTO the chat completion request
     * @return the chat completion response
     * @throws Exception if the API call fails
     */
    ChatCompletionSyncResponseDTO completions(ChatCompletionRequestDTO requestDTO) throws Exception;
}
