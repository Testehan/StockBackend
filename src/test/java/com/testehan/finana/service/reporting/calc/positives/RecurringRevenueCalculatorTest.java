package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.finstatement.BalanceSheetData;
import com.testehan.finana.model.finstatement.BalanceSheetReport;
import com.testehan.finana.model.finstatement.RevenueSegmentationData;
import com.testehan.finana.model.finstatement.RevenueSegmentationReport;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.RevenueSegmentationDataRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecurringRevenueCalculator Tests")
class RecurringRevenueCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    @Mock
    private BalanceSheetRepository balanceSheetRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource recurringRevenuePrompt;

    private RecurringRevenueCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RecurringRevenueCalculator(
                companyOverviewRepository,
                secFilingRepository,
                revenueSegmentationDataRepository,
                balanceSheetRepository,
                llmService,
                eventPublisher
        );

        try {
            String promptTemplate = "Test prompt with {company_name}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(recurringRevenuePrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
        }

        ReflectionTestUtils.setField(calculator, "recurringRevenuePrompt", recurringRevenuePrompt);
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("Should return error when company overview not found")
    void shouldReturnErrorWhenCompanyOverviewNotFound() {
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle missing data gracefully")
    void shouldHandleMissingDataGracefully() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(revenueSegmentationDataRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(balanceSheetRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();
        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenReturn("{\"score\":7,\"explanation\":\"Test\"}");

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
        when(revenueSegmentationDataRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(balanceSheetRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();
        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
    }
}