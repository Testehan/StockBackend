package com.testehan.finana.config;

import com.google.genai.Client;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {
    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @Value("${spring.ai.google.genai.chat.options.model}")
    private String modelName;

    @Bean
    @Primary
    public ChatModel googleGenAiChatModel() {
        return GoogleGenAiChatModel.builder()
                .genAiClient(Client.builder().apiKey(apiKey).build())
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.1)
                        .build())
                .build();
    }
}
