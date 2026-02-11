package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import com.testehan.finana.util.SafeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FinancialResilienceCalculator Tests")
class FinancialResilienceCalculatorTest {

    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private BalanceSheetRepository balanceSheetRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private LlmService llmService;
    @Mock
    private SafeParser safeParser;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource financialResiliencePrompt;

    private FinancialResilienceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new FinancialResilienceCalculator(
                incomeStatementRepository,
                balanceSheetRepository,
                llmService,
                eventPublisher,
                safeParser
        );

        try {
            String promptTemplate = "Test prompt {totalCashAndEquivalents} {totalDebt} {ttmEbitda} {ttmInterestExpense} {format}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(financialResiliencePrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
        }

        ReflectionTestUtils.setField(calculator, "financialResiliencePrompt", financialResiliencePrompt);
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("Should handle missing statement data gracefully and return result")
    void shouldHandleMissingDataGracefully() {
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(balanceSheetRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();
        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenReturn("{\"score\":7,\"explanation\":\"Test\"}");

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isNotEqualTo(-10);
    }

    @Test
    @DisplayName("Should return error when LLM call fails")
    void shouldReturnErrorWhenLlmCallFails() {
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(balanceSheetRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();
        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
    }
}