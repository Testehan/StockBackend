package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.llm.responses.FerolMoatAnalysisLlmResponse;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
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
@DisplayName("MoatCalculator Tests")
class MoatCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource moatPrompt;
    @Mock
    private Resource moat100BaggerPrompt;

    private MoatCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MoatCalculator(
                companyOverviewRepository,
                secFilingRepository,
                llmService,
                eventPublisher
        );

        try {
            String promptTemplate = "Test prompt with {business_description}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(moatPrompt.getInputStream()).thenReturn(inputStream);
            lenient().when(moat100BaggerPrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
        }

        ReflectionTestUtils.setField(calculator, "moatPrompt", moatPrompt);
        ReflectionTestUtils.setField(calculator, "moat100BaggerPrompt", moat100BaggerPrompt);
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("Should handle missing SEC filing data gracefully")
    void shouldHandleMissingSecFilingData() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        companyOverview.setDescription("Technology company");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        when(llmService.callLlm(any(Prompt.class), anyString(), anyString())).thenReturn("""
                {
                  "networkEffectScore": 8,
                  "networkEffectExplanation": "Test",
                  "switchingCostsScore": 8,
                  "switchingCostsExplanation": "Test",
                  "durableCostAdvantageScore": 8,
                  "durableCostAdvantageExplanation": "Test",
                  "intangiblesScore": 8,
                  "intangiblesExplanation": "Test",
                  "counterPositioningScore": 8,
                  "counterPositioningExplanation": "Test",
                  "moatDirectionScore": 8,
                  "moatDirectionExplanation": "Test"
                }
                """);

        FerolMoatAnalysisLlmResponse result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getNetworkEffectScore()).isNotEqualTo(-10);
    }

    @Test
    @DisplayName("Should return error when LLM call fails")
    void shouldReturnErrorWhenLlmCallFails() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        companyOverview.setDescription("Technology company");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        when(llmService.callLlm(any(Prompt.class), anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        FerolMoatAnalysisLlmResponse result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getNetworkEffectScore()).isEqualTo(-10);
    }

    @Test
    @DisplayName("Should calculate 100 Bagger moat correctly")
    void shouldCalculate100BaggerMoatCorrectly() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        companyOverview.setDescription("Technology company");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        when(llmService.callLlm(any(Prompt.class), anyString(), anyString())).thenReturn("{\"score\":8,\"explanation\":\"Test moat\"}");

        ReportItem result = calculator.calculate100BaggerMoat("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(8);
        assertThat(result.getExplanation()).contains("Test moat");
    }

    @Test
    @DisplayName("Should return error for 100 Bagger moat when LLM fails")
    void shouldReturnErrorFor100BaggerMoatWhenLlmFails() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        companyOverview.setDescription("Technology company");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        when(llmService.callLlm(any(Prompt.class), anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        ReportItem result = calculator.calculate100BaggerMoat("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
    }
}