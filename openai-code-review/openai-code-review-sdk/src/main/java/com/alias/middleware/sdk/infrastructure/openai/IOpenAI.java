package com.alias.middleware.sdk.infrastructure.openai;


import com.alias.middleware.sdk.infrastructure.openai.dto.ChatCompletionRequestDTO;
import com.alias.middleware.sdk.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;

public interface IOpenAI {

    ChatCompletionSyncResponseDTO completions(ChatCompletionRequestDTO requestDTO) throws Exception;

}
