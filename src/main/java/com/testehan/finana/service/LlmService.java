package com.testehan.finana.service;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class LlmService {

    private final ChatModel chatModel;

    public LlmService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String callLlm(String query) {
        return chatModel.call(new Prompt(new UserMessage(query))).getResult().getOutput().getText();
    }

    public String callLlm(Prompt query) {
        return chatModel.call(query).getResult().getOutput().getText();
    }

    public String callLlmWithSearch(String query) {
        var options = GoogleGenAiChatOptions.builder()
                .googleSearchRetrieval(true)
                .temperature(0.2d)
                .build();
        return chatModel.call(new Prompt(new UserMessage(query), options)).getResult().getOutput().getText();
    }

    public String callLlmLast(Prompt query) {
        return chatModel.call(new Prompt(new UserMessage(query.getContents()))).getResult().getOutput().getText();
    }

    public Flux<String> streamLlm(Prompt prompt) {
        return chatModel.stream(prompt)
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null && chatResponse.getResult().getOutput().getText() != null) {
                        return chatResponse.getResult().getOutput().getText();
                    }
                    return "";
                });
    }

    public Flux<String> streamLlmWithSearch(Prompt prompt) {
        var options = GoogleGenAiChatOptions.builder()
                .googleSearchRetrieval(true)
                .build();
        return chatModel.stream(new Prompt(prompt.getContents(), options))
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null && chatResponse.getResult().getOutput().getText() != null) {
                        return chatResponse.getResult().getOutput().getText();
                    }
                    return "";
                });
    }
}
