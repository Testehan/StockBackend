package com.testehan.finana.service;

import com.testehan.finana.exception.InsufficientCreditException;
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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    @Value("${app.llm.use-ollama:true}")
    private boolean useOllama;

    private final ChatModel chatModel;
    private final ChatClient ollamaChatClient;
    private final LlmCostService llmCostService;
    private final UserCreditService userCreditService;
    private final ChatClient chatClientWithTools;
    private final StockDataTools stockDataTools;

    public LlmService(
            @Qualifier("googleGenAiChatModel") ObjectProvider<ChatModel> chatModelProvider,
            ChatClient.Builder chatClientBuilder,
            LlmCostService llmCostService, 
            UserCreditService userCreditService,
            StockDataTools stockDataTools) {
        this.chatModel = chatModelProvider.getIfAvailable();
        this.llmCostService = llmCostService;
        this.userCreditService = userCreditService;
        
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
        String userEmail = getUserEmailFromContext();
        checkCredit(userEmail);

        if (chatModel == null) {
            return callLlmWithOllama(query, operationType, stockTicker);
        }
        try {
            ChatResponse response = chatModel.call(new Prompt(new UserMessage(query)));
            llmCostService.logUsage(userEmail, response, operationType, stockTicker);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCostService.logUsageFailure(userEmail, operationType, stockTicker, e.getMessage());
            throw e;
        }
    }

    private void checkCredit(String userEmail) {
        if (userEmail == null) {
            throw new InsufficientCreditException("No authenticated user found. Please log in to use this feature.");
        }

        logger.debug("Checking credit for user: {}", userEmail);
        if (!userCreditService.hasAnyCredit(userEmail)) {
            BigDecimal currentCredit = userCreditService.getCredit(userEmail);
            throw new InsufficientCreditException("Insufficient credit. Current balance: $" + currentCredit);
        }
    }

    private String getUserEmailFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.debug("Authentication in SecurityContext: {}", auth);
        
        if (auth == null) {
            return null;
        }
        
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String email = jwt.getClaimAsString("email");
            logger.debug("Extracted email from JWT: {}", email);
            return email;
        }
        
        logger.debug("Authentication type: {}", auth.getClass().getName());
        return null;
    }

    public String callLlm(Prompt query, String operationType, String stockTicker) {
        String userEmail = getUserEmailFromContext();
        checkCredit(userEmail);
        if (chatModel == null) {
            return callLlmWithOllama(query, operationType, stockTicker);
        }
        try {
            ChatResponse response = chatModel.call(query);
            llmCostService.logUsage(userEmail, response, operationType, stockTicker);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCostService.logUsageFailure(userEmail, operationType, stockTicker, e.getMessage());
            throw e;
        }
    }
// TODO When this is called for getting the sentiment analysis.. you should try and see if you can get the urls that the
//  call uses during the googleSearchRetrieval from the response metadata...and then put those in the sentiment object.
//  ..instead of getting the URLS in the generated reponse..which is making the total cost higher...right now it is at
//  about 7 cents per sentiment call...
    public String callLlmWithSearch(String query, String operationType, String stockTicker) {
        String userEmail = getUserEmailFromContext();
        checkCredit(userEmail);
        try {
            var options = GoogleGenAiChatOptions.builder()
                    .googleSearchRetrieval(true)
                    .temperature(0.2d)
                    .build();
            ChatResponse response = chatModel.call(new Prompt(new UserMessage(query), options));
            llmCostService.logUsage(userEmail, response, operationType, stockTicker);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCostService.logUsageFailure(userEmail, operationType, stockTicker, e.getMessage());
            throw e;
        }
    }


    public Flux<String> streamLlm(Prompt prompt, String operationType, String stockTicker) {
        String userEmail = getUserEmailFromContext();
        checkCredit(userEmail);
        return chatModel.stream(prompt)
                .collectList()
                .map(responses -> {
                    ChatResponse lastResponse = responses.get(responses.size() - 1);
                    llmCostService.logUsage(userEmail, lastResponse, operationType, stockTicker);
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
                    llmCostService.logUsageFailure(userEmail, operationType, stockTicker, e.getMessage());
                    return Flux.empty();
                });
    }

    public Flux<String> streamLlmWithSearch(Prompt prompt, String operationType, String symbol) {
        String userEmail = getUserEmailFromContext();
        checkCredit(userEmail);
        var options = GoogleGenAiChatOptions.builder()
                .googleSearchRetrieval(true)
                .build();
        return chatModel.stream(new Prompt(prompt.getContents(), options))
                .collectList()
                .map(responses -> {
                    ChatResponse lastResponse = responses.get(responses.size() - 1);
                    llmCostService.logUsage(userEmail, lastResponse, operationType, symbol);
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
                    llmCostService.logUsageFailure(userEmail, operationType, symbol, e.getMessage());
                    return Flux.empty();
                });
    }

    public String callLlmWithTools(String question, String operationType, String stockTicker) {
        String userEmail = getUserEmailFromContext();
        checkCredit(userEmail);
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

            var extractedTicker = tickerHolder.get("ticker");
            String tickerToLog = extractedTicker != null ? extractedTicker : stockTicker;

            llmCostService.logUsage(userEmail, chatResponse, operationType, tickerToLog);
            return chatResponse.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCostService.logUsageFailure(userEmail, operationType, stockTicker, e.getMessage());
            throw e;
        }
    }

    // ==================== Ollama Methods ====================
    // In local mode (app.llm.use-ollama=true) these call the local Ollama instance.
    // In production (app.llm.use-ollama=false) they transparently delegate to Gemini equivalents.

    public String callLlmWithOllama(String query) {
        return callLlmWithOllama(query, "ollama_development", "ollama_development");
    }

    public String callLlmWithOllama(String query, String operationType, String stockTicker) {
        if (!useOllama) {
            return callLlm(query, operationType, stockTicker);
        }
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
        if (!useOllama) {
            return callLlm(query, operationType, stockTicker);
        }
        try {
            String response = ollamaChatClient.prompt(query).call().content();
            logger.info("Ollama call completed for operation: {}, symbol: {}", operationType, stockTicker);
            return response;
        } catch (Exception e) {
            logger.error("Ollama call failed for operation: {}, symbol: {}, error: {}", operationType, stockTicker, e.getMessage());
            throw e;
        }
    }

    public String callLlmWithOllama(String systemPrompt, List<Message> messages) {
        if (!useOllama) {
            List<Message> allMessages = new ArrayList<>();
            allMessages.add(new SystemMessage(systemPrompt));
            allMessages.addAll(messages);
            return callLlm(new Prompt(allMessages), "chat", "chat");
        }
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

    public Flux<String> streamLlmWithOllama(Prompt prompt, String operationType, String stockTicker) {
        if (!useOllama) {
            return streamLlm(prompt, operationType, stockTicker);
        }
        return ollamaChatClient.prompt(prompt)
                .stream()
                .content()
                .doOnComplete(() -> logger.info("Ollama stream completed for operation: {}, symbol: {}", operationType, stockTicker))
                .doOnError(e -> logger.error("Ollama stream failed for operation: {}, symbol: {}, error: {}", operationType, stockTicker, e.getMessage()));
    }

    public String callLlmWithOllamaAndTools(String systemPrompt, List<Message> messages) {
        if (!useOllama) {
            String userEmail = getUserEmailFromContext();
            checkCredit(userEmail);
            try {
                var chatResponse = chatClientWithTools.prompt()
                        .system(systemPrompt)
                        .messages(messages)
                        .tools(stockDataTools)
                        .call()
                        .chatResponse();
                llmCostService.logUsage(userEmail, chatResponse, "chat_with_tools", "chat");
                return chatResponse.getResult().getOutput().getText();
            } catch (Exception e) {
                llmCostService.logUsageFailure(userEmail, "chat_with_tools", "chat", e.getMessage());
                throw e;
            }
        }
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
