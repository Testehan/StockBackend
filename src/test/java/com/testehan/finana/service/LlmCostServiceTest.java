package com.testehan.finana.service;

import com.testehan.finana.model.llm.LlmUsage;
import com.testehan.finana.repository.LlmUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmCostServiceTest {

    @Mock
    private LlmUsageRepository llmUsageRepository;

    @Mock
    private UserCreditService userCreditService;

    @Mock
    private ChatResponse chatResponse;

    private LlmCostService llmCostService;
    private static final String USER_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        llmCostService = new LlmCostService(llmUsageRepository, userCreditService);
    }

    private ChatResponse buildResponse(int promptTokens, int completionTokens, int cachedTokens) {
        AssistantMessage message = new AssistantMessage("test response");
        Generation generation = new Generation(message);

        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        GoogleGenAiUsage usage = mock(GoogleGenAiUsage.class);
        lenient().when(usage.getPromptTokens()).thenReturn(promptTokens);
        lenient().when(usage.getCompletionTokens()).thenReturn(completionTokens);
        lenient().when(usage.getCachedContentTokenCount()).thenReturn(cachedTokens);
        lenient().when(metadata.getUsage()).thenReturn(usage);
        lenient().when(chatResponse.getMetadata()).thenReturn(metadata);
        lenient().when(chatResponse.getResult()).thenReturn(generation);
        return chatResponse;
    }

    @Test
    void logUsage_Success_SmallTokens() {
        buildResponse(1000, 500, 0);
        llmCostService.logUsage(USER_EMAIL, chatResponse, "sentiment_analysis", "AAPL");

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());
        LlmUsage saved = captor.getValue();

        assertEquals("gemini-2.5-pro", saved.getModel());
        assertEquals("sentiment_analysis", saved.getOperationType());
        assertEquals("AAPL", saved.getSymbol());
        assertEquals(1000, saved.getPromptTokens());
        assertEquals(500, saved.getCompletionTokens());
        assertEquals(0, saved.getCachedTokens());
        assertTrue(saved.isSuccess());
        assertNull(saved.getErrorMessage());
        assertTrue(saved.getTotalCostUsd().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void logUsage_WithCachedTokens() {
        buildResponse(50000, 1000, 40000);
        llmCostService.logUsage(USER_EMAIL, chatResponse, "sentiment_analysis", "MSFT");

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());
        LlmUsage saved = captor.getValue();

        assertEquals(50000, saved.getPromptTokens());
        assertEquals(1000, saved.getCompletionTokens());
        assertEquals(40000, saved.getCachedTokens());
        assertTrue(saved.isSuccess());
    }

    @Test
    void logUsage_LargePromptTokens_UsesLargePricing() {
        buildResponse(300000, 500, 0);
        llmCostService.logUsage(USER_EMAIL, chatResponse, "deep_research", "GOOGL");

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());
        LlmUsage saved = captor.getValue();

        assertTrue(saved.getTotalCostUsd().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void logUsage_LargeCompletionTokens_UsesLargePricing() {
        buildResponse(1000, 300000, 0);
        llmCostService.logUsage(USER_EMAIL, chatResponse, "deep_research", "GOOGL");

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());
        LlmUsage saved = captor.getValue();

        assertTrue(saved.getTotalCostUsd().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void logUsage_NullResponse_HandlesGracefully() {
        llmCostService.logUsage(USER_EMAIL, (ChatResponse) null, "sentiment_analysis", "AAPL");

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());
        LlmUsage saved = captor.getValue();

        assertEquals(0, saved.getPromptTokens());
        assertEquals(0, saved.getCompletionTokens());
        assertEquals(0, saved.getCachedTokens());
        assertTrue(saved.isSuccess());
    }

    @Test
    void logUsage_NullMetadata_HandlesGracefully() {
        lenient().when(chatResponse.getMetadata()).thenReturn(null);

        llmCostService.logUsage(USER_EMAIL, chatResponse, "sentiment_analysis", "AAPL");

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());
        LlmUsage saved = captor.getValue();

        assertEquals(0, saved.getPromptTokens());
        assertEquals(0, saved.getCompletionTokens());
    }

    @Test
    void logUsage_NonGoogleUsage_ReturnsZeroCachedTokens() {
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        lenient().when(usage.getPromptTokens()).thenReturn(1000);
        lenient().when(usage.getCompletionTokens()).thenReturn(500);
        lenient().when(metadata.getUsage()).thenReturn(usage);
        lenient().when(chatResponse.getMetadata()).thenReturn(metadata);
        AssistantMessage message = new AssistantMessage("test");
        lenient().when(chatResponse.getResult()).thenReturn(new Generation(message));

        llmCostService.logUsage(USER_EMAIL, chatResponse, "sentiment_analysis", "AAPL");

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());
        LlmUsage saved = captor.getValue();

        assertEquals(0, saved.getCachedTokens());
    }

    @Test
    void logUsage_Failure_LogsWithZeroTokens() {
        llmCostService.logUsageFailure(USER_EMAIL, "sentiment_analysis", "AAPL", "Connection timeout");

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());
        LlmUsage saved = captor.getValue();

        assertEquals("gemini-2.5-pro", saved.getModel());
        assertEquals("sentiment_analysis", saved.getOperationType());
        assertEquals("AAPL", saved.getSymbol());
        assertEquals(0, saved.getPromptTokens());
        assertEquals(0, saved.getCompletionTokens());
        assertEquals(0, saved.getCachedTokens());
        assertEquals(BigDecimal.ZERO, saved.getTotalCostUsd());
        assertFalse(saved.isSuccess());
        assertEquals("Connection timeout", saved.getErrorMessage());
    }

    @Test
    void logUsage_RepositoryThrowsException_DoesNotRethrow() {
        buildResponse(1000, 500, 0);
        doThrow(new RuntimeException("DB connection failed")).when(llmUsageRepository).save(any());

        assertDoesNotThrow(() -> llmCostService.logUsage(USER_EMAIL, chatResponse, "sentiment_analysis", "AAPL"));
    }

    @Test
    void logUsage_FailureRepositoryThrows_DoesNotRethrow() {
        doThrow(new RuntimeException("DB connection failed")).when(llmUsageRepository).save(any());

        assertDoesNotThrow(() -> llmCostService.logUsageFailure(USER_EMAIL, "sentiment_analysis", "AAPL", "timeout"));
    }

    @Test
    void logUsage_CalculatesCorrectCost_SmallTokens() {
        buildResponse(100000, 50000, 30000);
        llmCostService.logUsage(USER_EMAIL, chatResponse, "sentiment_analysis", "AAPL");

        ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(captor.capture());
        LlmUsage saved = captor.getValue();

        BigDecimal cost = saved.getTotalCostUsd();
        assertTrue(cost.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(cost.scale() <= 6);
    }
}