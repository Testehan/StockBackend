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
@DisplayName("CapitalAllocationCalculator Tests")
class CapitalAllocationCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private FinancialRatiosRepository financialRatiosRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private LlmService llmService;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource capitalAllocationPrompt;

    private CapitalAllocationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CapitalAllocationCalculator(
                companyOverviewRepository,
                incomeStatementRepository,
                financialRatiosRepository,
                secFilingRepository,
                eventPublisher,
                llmService
        );

        try {
            String promptTemplate = "Test prompt with {company_name}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(capitalAllocationPrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
        }

        ReflectionTestUtils.setField(calculator, "capitalAllocationPrompt", capitalAllocationPrompt);
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
    @DisplayName("Should handle missing financial ratios gracefully")
    void shouldHandleMissingFinancialRatios() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        
        IncomeStatementData incomeStatementData = new IncomeStatementData();
        List<IncomeReport> annualReports = new ArrayList<>();
        IncomeReport report = new IncomeReport();
        report.setDate("2024-01-01");
        report.setWeightedAverageShsOutDil("1000000");
        report.setRevenue("100000");
        annualReports.add(report);
        
        IncomeReport report2 = new IncomeReport();
        report2.setDate("2020-01-01");
        report2.setWeightedAverageShsOutDil("900000");
        report2.setRevenue("80000");
        annualReports.add(report2);
        
        incomeStatementData.setAnnualReports(annualReports);
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.of(incomeStatementData));
        
        mockEventPublisher();
        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenReturn("{\"score\":5,\"explanation\":\"Test\"}");

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
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
    }

    @Test
    @DisplayName("Should calculate net debt to EBITDA correctly for safe level")
    void shouldCalculateNetDebtToEbitdaSafe() {
        FinancialRatiosData financialRatiosData = new FinancialRatiosData();
        List<FinancialRatiosReport> annualReports = new ArrayList<>();
        
        FinancialRatiosReport report = new FinancialRatiosReport();
        report.setDate("2024-01-01");
        report.setNetDebtToEbitda(new BigDecimal("2.0"));
        annualReports.add(report);
        
        financialRatiosData.setAnnualReports(annualReports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(financialRatiosData));

        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();
        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenReturn("{\"score\":5,\"explanation\":\"Test\"}");

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getExplanation()).contains("Pretty safe");
    }

    @Test
    @DisplayName("Should calculate net debt to EBITDA correctly for net cash position")
    void shouldCalculateNetDebtToEbitdaNetCash() {
        FinancialRatiosData financialRatiosData = new FinancialRatiosData();
        List<FinancialRatiosReport> annualReports = new ArrayList<>();
        
        FinancialRatiosReport report = new FinancialRatiosReport();
        report.setDate("2024-01-01");
        report.setNetDebtToEbitda(new BigDecimal("-1.5"));
        annualReports.add(report);
        
        financialRatiosData.setAnnualReports(annualReports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(financialRatiosData));

        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();
        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenReturn("{\"score\":5,\"explanation\":\"Test\"}");

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getExplanation()).contains("Net cash position");
    }
}