package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CyclicalityCalculator Tests")
class CyclicalityCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private FinancialRatiosRepository financialRatiosRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource companyCyclicalityPrompt;

    private CyclicalityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CyclicalityCalculator(
                companyOverviewRepository,
                secFilingRepository,
                incomeStatementRepository,
                financialRatiosRepository,
                llmService,
                eventPublisher
        );

        try {
            String promptTemplate = "Test prompt with {company_name} and {business_description}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(companyCyclicalityPrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
        }

        ReflectionTestUtils.setField(calculator, "companyCyclicalityPrompt", companyCyclicalityPrompt);
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

        assertThat(result.getScore()).isEqualTo(-10);
    }

    @Test
    @DisplayName("Should handle missing SEC filing data gracefully")
    void shouldHandleMissingSecFilingData() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        companyOverview.setIndustry("Technology");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        
        IncomeStatementData incomeStatementData = new IncomeStatementData();
        List<IncomeReport> annualReports = new ArrayList<>();
        IncomeReport report = new IncomeReport();
        report.setDate("2024-01-01");
        report.setRevenue("100000");
        annualReports.add(report);
        incomeStatementData.setAnnualReports(annualReports);
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.of(incomeStatementData));

        FinancialRatiosData financialRatiosData = new FinancialRatiosData();
        List<FinancialRatiosReport> ratiosReports = new ArrayList<>();
        FinancialRatiosReport ratioReport = new FinancialRatiosReport();
        ratioReport.setDate("2024-01-01");
        ratioReport.setOperatingProfitMargin(new BigDecimal("0.25"));
        ratiosReports.add(ratioReport);
        financialRatiosData.setAnnualReports(ratiosReports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(financialRatiosData));
        
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
        companyOverview.setIndustry("Technology");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
    }
}