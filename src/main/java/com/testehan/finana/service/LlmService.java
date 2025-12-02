package com.testehan.finana.service;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class LlmService {

    private final ChatModel chatModel;
    private final LlmCostService llmCostService;

    public LlmService(ChatModel chatModel, LlmCostService llmCostService) {
        this.chatModel = chatModel;
        this.llmCostService = llmCostService;
    }

    public String callLlm(String query) {
        return callLlm(query, "development_endpoint", "development_endpoint");
    }

    public String callLlm(String query, String operationType, String stockTicker) {
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
        try {
            ChatResponse response = chatModel.call(query);
            llmCostService.logUsage(response, operationType, stockTicker);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCostService.logUsage(operationType, stockTicker, e.getMessage());
            throw e;
        }
    }

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
}
