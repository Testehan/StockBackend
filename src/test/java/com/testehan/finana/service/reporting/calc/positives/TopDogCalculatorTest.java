package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
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
@DisplayName("TopDogCalculator Tests")
class TopDogCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private OptionalityCalculator optionalityCalculator;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource topDogPrompt;

    private TopDogCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TopDogCalculator(
                companyOverviewRepository,
                secFilingRepository,
                llmService,
                eventPublisher,
                optionalityCalculator
        );

        try {
            String promptTemplate = "Test prompt with {company_name}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(topDogPrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
        }

        ReflectionTestUtils.setField(calculator, "topDogPrompt", topDogPrompt);
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
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(optionalityCalculator.getLatestEarningsTranscript("AAPL")).thenReturn("Test transcript");
        mockEventPublisher();
        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenReturn("{\"score\":8,\"explanation\":\"Test\"}");

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isNotEqualTo(-10);
    }

    @Test
    @DisplayName("Should return error when LLM call fails")
    void shouldReturnErrorWhenLlmCallFails() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(optionalityCalculator.getLatestEarningsTranscript("AAPL")).thenReturn("Test transcript");
        mockEventPublisher();
        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
    }
}