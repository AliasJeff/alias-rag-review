package com.alias.middleware.sdk.infrastructure.openai.impl;

import com.alibaba.fastjson2.JSON;
import com.alias.middleware.sdk.infrastructure.openai.IOpenAI;
import com.alias.middleware.sdk.infrastructure.openai.dto.ChatCompletionRequestDTO;
import com.alias.middleware.sdk.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OpenAI implements IOpenAI {

    private final String apiHost;
    private final String apiKey;

    /**
     * 构造 OpenAI 客户端
     *
     * @param apiHost API 地址，默认: https://api.openai.com/v1/chat/completions
     * @param apiKey  API Key
     */
    public OpenAI(String apiHost, String apiKey) {
        this.apiHost = apiHost;
        this.apiKey = apiKey;
    }

    @Override
    public ChatCompletionSyncResponseDTO completions(ChatCompletionRequestDTO requestDTO) throws Exception {
        URL url = new URL(apiHost);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "alias-openai-code-review-sdk");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = JSON.toJSONString(requestDTO).getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            // 读取错误响应
            StringBuilder errorResponse = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream() != null 
                        ? connection.getErrorStream() 
                        : connection.getInputStream(), StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = errorReader.readLine()) != null) {
                    errorResponse.append(inputLine);
                }
            } finally {
                connection.disconnect();
            }
            throw new RuntimeException("OpenAI API request failed, code=" + responseCode + ", error=" + errorResponse);
        }

        // 读取成功响应
        StringBuilder content = new StringBuilder();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
        } finally {
            connection.disconnect();
        }

        return JSON.parseObject(content.toString(), ChatCompletionSyncResponseDTO.class);
    }

}
