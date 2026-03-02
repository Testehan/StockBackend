package com.testehan.finana.service;

import com.testehan.finana.service.mcp.StockDataTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    private final ChatModel chatModel;
    private final ChatClient ollamaChatClient;
    private final LlmCostService llmCostService;
    private final ChatClient chatClientWithTools;
    private final StockDataTools stockDataTools;

    public LlmService(
            @Qualifier("googleGenAiChatModel") ObjectProvider<ChatModel> chatModelProvider,
            ChatClient.Builder chatClientBuilder,
            LlmCostService llmCostService, StockDataTools stockDataTools) {
        this.chatModel = chatModelProvider.getIfAvailable();
        this.llmCostService = llmCostService;
        
        // This ChatClient will use the auto-configured Ollama model via spring.ai.model.chat.type=ollama
        this.ollamaChatClient = chatClientBuilder.build();
        this.stockDataTools = stockDataTools;

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .temperature(0.1)
                .googleSearchRetrieval(false)
                .build();
        
        this.chatClientWithTools = chatClientBuilder
                .defaultOptions(options)
                .build();
    }

    public String callLlm(String query) {
        return callLlm(query, "development_endpoint", "development_endpoint");
    }

    public String callLlm(String query, String operationType, String stockTicker) {
        if (chatModel == null) {
            return callLlmWithOllama(query, operationType, stockTicker);
        }
        try {
            ChatResponse response = chatModel.call(new Prompt(new UserMessage(query)));
            llmCostService.logUsage(response, operationType, stockTicker);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCostService.logUsage(operationType, stockTicker, e.getMessage());
            throw e;
        }
    }

    public String callLlm(Prompt query, String operationType, String stockTicker) {
        if (chatModel == null) {
            return callLlmWithOllama(query, operationType, stockTicker);
        }
        try {
            ChatResponse response = chatModel.call(query);
            llmCostService.logUsage(response, operationType, stockTicker);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCostService.logUsage(operationType, stockTicker, e.getMessage());
            throw e;
        }
    }
// TODO When this is called for getting the sentiment analysis.. you should try and see if you can get the urls that the
//  call uses during the googleSearchRetrieval from the response metadata...and then put those in the sentiment object.
//  ..instead of getting the URLS in the generated reponse..which is making the total cost higher...right now it is at
//  about 7 cents per sentiment call...
    public String callLlmWithSearch(String query, String operationType, String stockTicker) {
        try {
            var options = GoogleGenAiChatOptions.builder()
                    .googleSearchRetrieval(true)
                    .temperature(0.2d)
                    .build();
            ChatResponse response = chatModel.call(new Prompt(new UserMessage(query), options));
            llmCostService.logUsage(response, operationType, stockTicker);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCostService.logUsage(operationType, stockTicker, e.getMessage());
            throw e;
        }
    }


    public Flux<String> streamLlm(Prompt prompt, String operationType, String stockTicker) {
        return chatModel.stream(prompt)
                .collectList()
                .map(responses -> {
                    ChatResponse lastResponse = responses.get(responses.size() - 1);
                    llmCostService.logUsage(lastResponse, operationType, stockTicker);
                    return responses;
                })
                .flatMapMany(responses -> Flux.fromIterable(responses))
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null && chatResponse.getResult().getOutput().getText() != null) {
                        return chatResponse.getResult().getOutput().getText();
                    }
                    return "";
                })
                .onErrorResume(e -> {
                    llmCostService.logUsage(operationType, stockTicker, e.getMessage());
                    return Flux.empty();
                });
    }

    public Flux<String> streamLlmWithSearch(Prompt prompt, String operationType, String symbol) {
        var options = GoogleGenAiChatOptions.builder()
                .googleSearchRetrieval(true)
                .build();
        return chatModel.stream(new Prompt(prompt.getContents(), options))
                .collectList()
                .map(responses -> {
                    ChatResponse lastResponse = responses.get(responses.size() - 1);
                    llmCostService.logUsage(lastResponse, operationType, symbol);
                    return responses;
                })
                .flatMapMany(responses -> Flux.fromIterable(responses))
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null && chatResponse.getResult().getOutput().getText() != null) {
                        return chatResponse.getResult().getOutput().getText();
                    }
                    return "";
                })
                .onErrorResume(e -> {
                    llmCostService.logUsage(operationType, symbol, e.getMessage());
                    return Flux.empty();
                });
    }

    public String callLlmWithTools(String question, String operationType, String stockTicker) {
        String systemPrompt = """
            You are a financial analyst assistant. When the user asks about a stock,
            you must use the available tools to fetch data from the database.
            Extract the stock ticker from the question and use appropriate tools.
            Then provide a clear answer based on the data.
            """;

        try {
            Map<String, Object> toolContext = new HashMap<>();
            Map<String, String> tickerHolder = new HashMap<>();
            toolContext.put("ticker_holder", tickerHolder);
            
            var chatResponse = chatClientWithTools.prompt()
                    .system(systemPrompt)
                    .user(question)
                    .tools(stockDataTools)
                    .options(GoogleGenAiChatOptions.builder()
                            .temperature(0.1)
                            .googleSearchRetrieval(false)
                            .toolContext(toolContext)
                            .build())
                    .toolContext(toolContext)
                    .call()
                    .chatResponse();

            // Extract ticker from tool calls in memory
            var extractedTicker = tickerHolder.get("ticker");
            String tickerToLog = extractedTicker != null ? extractedTicker.toString() : stockTicker;

            llmCostService.logUsage(chatResponse, operationType, tickerToLog);
            return chatResponse.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCostService.logUsage(operationType, stockTicker, e.getMessage());
            throw e;
        }
    }

    // ==================== Ollama Methods ====================
    // These methods use the local Ollama model for testing purposes (no cost tracking)
    // Using ChatClient which is auto-configured via spring.ai.model.chat.type=ollama

    public String callLlmWithOllama(String query) {
        return callLlmWithOllama(query, "ollama_development", "ollama_development");
    }

    public String callLlmWithOllama(String systemPrompt, java.util.List<org.springframework.ai.chat.messages.Message> messages) {
        try {
            String response = ollamaChatClient.prompt()
                    .system(systemPrompt)
                    .messages(messages)
                    .call()
                    .content();
            logger.info("Ollama call with system prompt and chat memory completed successfully");
            return response;
        } catch (Exception e) {
            logger.error("Ollama call with system prompt and chat memory failed, error: {}", e.getMessage());
            throw e;
        }
    }

    public String callLlmWithOllama(String query, String operationType, String stockTicker) {
        try {
            String response = ollamaChatClient.prompt(query).call().content();
            logger.info("Ollama call completed for operation: {}, symbol: {}", operationType, stockTicker);
            return response;
        } catch (Exception e) {
            logger.error("Ollama call failed for operation: {}, symbol: {}, error: {}", operationType, stockTicker, e.getMessage());
            throw e;
        }
    }

    public String callLlmWithOllama(Prompt query, String operationType, String stockTicker) {
        try {
            String response = ollamaChatClient.prompt(query).call().content();
            logger.info("Ollama call completed for operation: {}, symbol: {}", operationType, stockTicker);
            return response;
        } catch (Exception e) {
            logger.error("Ollama call failed for operation: {}, symbol: {}, error: {}", operationType, stockTicker, e.getMessage());
            throw e;
        }
    }

    public Flux<String> streamLlmWithOllama(Prompt prompt, String operationType, String stockTicker) {
        return ollamaChatClient.prompt(prompt)
                .stream()
                .content()
                .doOnComplete(() -> logger.info("Ollama stream completed for operation: {}, symbol: {}", operationType, stockTicker))
                .doOnError(e -> logger.error("Ollama stream failed for operation: {}, symbol: {}, error: {}", operationType, stockTicker, e.getMessage()));
    }

    public String callLlmWithOllamaAndTools(String systemPrompt, java.util.List<Message> messages) {
        try {
            Map<String, Object> toolContext = new HashMap<>();
            Map<String, String> tickerHolder = new HashMap<>();
            toolContext.put("ticker_holder", tickerHolder);

            String response = ollamaChatClient.prompt()
                    .system(systemPrompt)
                    .messages(messages)
                    .tools(stockDataTools)
                    .toolContext(toolContext)
                    .call()
                    .content();
            logger.info("Ollama call with tools and chat memory completed successfully");
            return response;
        } catch (Exception e) {
            logger.error("Ollama call with tools and chat memory failed, error: {}", e.getMessage());
            throw e;
        }
    }
}
