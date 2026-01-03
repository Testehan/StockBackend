package com.testehan.finana.service;

import com.testehan.finana.service.mcp.StockDataTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private LlmCostService llmCostService;

    @Mock
    private StockDataTools stockDataTools;

    private LlmService llmService;

    @BeforeEach
    void setUp() {
        lenient().when(chatClientBuilder.build()).thenReturn(chatClient);
        lenient().when(chatClientBuilder.defaultOptions(any())).thenReturn(chatClientBuilder);
        llmService = new LlmService(chatModel, chatClientBuilder, llmCostService, stockDataTools);
    }

    private ChatResponse buildResponse(String text) {
        AssistantMessage assistantMessage = new AssistantMessage(text);
        Generation generation = new Generation(assistantMessage);
        ChatResponse response = mock(ChatResponse.class);
        lenient().when(response.getResult()).thenReturn(generation);
        return response;
    }

    @Test
    void callLlm_Success_ReturnsResponseText() {
        ChatResponse response = buildResponse("Test response");
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        String result = llmService.callLlm("Hello", "test_op", "AAPL");

        assertEquals("Test response", result);
        verify(llmCostService).logUsage(response, "test_op", "AAPL");
    }

    @Test
    void callLlm_Exception_LogsAndThrows() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("API error"));

        assertThrows(RuntimeException.class, () -> llmService.callLlm("Hello", "test_op", "AAPL"));
        verify(llmCostService).logUsage("test_op", "AAPL", "API error");
    }

    @Test
    void callLlmWithPrompt_Success_ReturnsResponseText() {
        ChatResponse response = buildResponse("Test response");
        Prompt prompt = new Prompt(new UserMessage("Hello"));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        String result = llmService.callLlm(prompt, "test_op", "AAPL");

        assertEquals("Test response", result);
    }

    @Test
    void callLlmWithSearch_Success_ReturnsResponse() {
        ChatResponse response = buildResponse("Search result");
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        String result = llmService.callLlmWithSearch("Find info", "search_op", "AAPL");

        assertEquals("Search result", result);
    }

    @Test
    void streamLlm_Success_ReturnsFlux() {
        ChatResponse resp1 = buildResponse("Part1");
        ChatResponse resp2 = buildResponse("Part2");
        ChatResponse lastResponse = buildResponse("Full response");

        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(resp1, resp2, lastResponse));

        Prompt prompt = new Prompt(new UserMessage("Hello"));
        List<String> results = llmService.streamLlm(prompt, "stream_op", "AAPL")
                .collectList()
                .block();

        assertNotNull(results);
        verify(llmCostService).logUsage(eq(lastResponse), eq("stream_op"), eq("AAPL"));
    }

    @Test
    void streamLlm_Error_LogsAndReturnsEmpty() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("Stream error")));

        Prompt prompt = new Prompt(new UserMessage("Hello"));

        llmService.streamLlm(prompt, "stream_op", "AAPL")
                .collectList()
                .block();

        verify(llmCostService).logUsage("stream_op", "AAPL", "Stream error");
    }

    @Test
    void streamLlmWithSearch_Success() {
        ChatResponse resp = buildResponse("Search result");
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(resp, resp));

        Prompt prompt = new Prompt(new UserMessage("Search"));
        llmService.streamLlmWithSearch(prompt, "search_stream", "AAPL")
                .collectList()
                .block();

        verify(llmCostService).logUsage(any(ChatResponse.class), eq("search_stream"), eq("AAPL"));
    }

    @Test
    void callLlm_DefaultParameters() {
        ChatResponse response = buildResponse("Default response");
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        String result = llmService.callLlm("Hello");

        assertEquals("Default response", result);
        verify(llmCostService).logUsage(response, "development_endpoint", "development_endpoint");
    }

    @Test
    void callLlm_NullResult_ThrowsException() {
        ChatResponse response = mock(ChatResponse.class);
        lenient().when(response.getResult()).thenReturn(null);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        assertThrows(NullPointerException.class, () -> llmService.callLlm("Hello", "op", "AAPL"));
    }
}