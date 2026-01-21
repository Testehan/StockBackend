package com.testehan.finana.controller;

import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.PortfolioService;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final String PORTFOLIO_NOT_AVAILABLE = "\n\nNote: Portfolio information is not available for this user.";

    private final LlmService llmService;
    private final PortfolioService portfolioService;
    private final ChatMemory chatMemory;

    private String systemPrompt;

    @Value("classpath:/prompts/chat/chat_system_prompt.txt")
    private Resource systemPromptResource;

    public ChatController(LlmService llmService, PortfolioService portfolioService, ChatMemory chatMemory) {
        this.llmService = llmService;
        this.portfolioService = portfolioService;
        this.chatMemory = chatMemory;
    }

    @PostConstruct
    void init() throws Exception {
        this.systemPrompt = StreamUtils.copyToString(systemPromptResource.getInputStream(), StandardCharsets.UTF_8);
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String userEmail = body.get("userEmail");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("Message is required");
        }

        String conversationId = userEmail != null ? userEmail : "anonymous";

        String fullSystemPrompt = systemPrompt;

        if (userEmail != null && !userEmail.isBlank()) {
            String portfolioAllocation = portfolioService.getPortfolioAllocation(userEmail);
            if (!portfolioAllocation.isEmpty()) {
                fullSystemPrompt += "\n\nUser Portfolio Allocation (percentages):\n" + portfolioAllocation;
            } else {
                fullSystemPrompt += PORTFOLIO_NOT_AVAILABLE;
            }
        } else {
            fullSystemPrompt += PORTFOLIO_NOT_AVAILABLE;
        }

        chatMemory.add(conversationId, new UserMessage(message));

        List<Message> chatHistory = chatMemory.get(conversationId);

        String response = llmService.callLlmWithOllama(fullSystemPrompt, chatHistory);

        chatMemory.add(conversationId, new AssistantMessage(response));

        return ResponseEntity.ok(Map.of("response", response));
    }

    @DeleteMapping("/{userEmail}")
    public ResponseEntity<?> clearChat(@PathVariable String userEmail) {
        chatMemory.clear(userEmail);
        return ResponseEntity.ok("Chat history cleared");
    }
}