package com.testehan.finana.service;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

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
        return chatModel.call(new Prompt(new UserMessage(query.getContents()))).getResult().getOutput().getText();
    }

}
