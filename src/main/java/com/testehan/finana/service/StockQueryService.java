package com.testehan.finana.service;

import com.testehan.finana.service.mcp.StockDataTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;

@Service
public class StockQueryService {

    private final ChatClient chatClient;
    private final StockDataTools stockDataTools;

    public StockQueryService(ChatClient.Builder chatClientBuilder, StockDataTools stockDataTools) {
        this.stockDataTools = stockDataTools;
        
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .temperature(0.1)
                .googleSearchRetrieval(false)
                .build();
        
        this.chatClient = chatClientBuilder
                .defaultOptions(options)
                .build();
    }

    public String queryStock(String question) {
        String systemPrompt = """
            You are a financial analyst assistant. When the user asks about a stock,
            you must use the available tools to fetch data from the database.
            Extract the stock ticker from the question and use appropriate tools.
            Then provide a clear answer based on the data.
            """;

        var toolCallbacks = ToolCallbacks.from(stockDataTools);

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .toolCallbacks(toolCallbacks)
                .call()
                .content();

        return response;
    }
}
