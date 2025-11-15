package com.alias.rag.dev.tech.config;

import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIConfig {

  @Bean
  public TokenTextSplitter tokenTextSplitter() {
    return new TokenTextSplitter();
  }

  @Bean
  public OpenAiApi openAiApi(
      @Value("${spring.ai.openai.base-url}") String baseUrl,
      @Value("${spring.ai.openai.api-key}") String apikey) {
    return OpenAiApi.builder().baseUrl(baseUrl).apiKey(apikey).build();
  }

  @Bean("openAiSimpleVectorStore")
  public SimpleVectorStore vectorStore(OpenAiApi openAiApi) {
    OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi);
    return SimpleVectorStore.builder(embeddingModel).build();
  }
}
