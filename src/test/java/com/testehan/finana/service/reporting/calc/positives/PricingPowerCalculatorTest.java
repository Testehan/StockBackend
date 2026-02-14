package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.filing.TenKFilings;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PricingPowerCalculator Tests")
class PricingPowerCalculatorTest {

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
    private OptionalityCalculator optionalityCalculator;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource pricingPowerPrompt;

    private PricingPowerCalculator calculator;

    @BeforeEach
    void setUp() throws IOException {
        calculator = new PricingPowerCalculator(
                companyOverviewRepository,
                secFilingRepository,
                incomeStatementRepository,
                financialRatiosRepository,
                llmService,
                eventPublisher,
                optionalityCalculator
        );

        String promptContent = "Analyze pricing power for {company_name}. Business: {business_description}. Management Discussion: {management_discussion}. Financials: {financial_table}. Transcript: {latest_earnings_transcript}. Format: {format}";
        lenient().when(pricingPowerPrompt.getInputStream()).thenReturn(new ByteArrayInputStream(promptContent.getBytes()));
        ReflectionTestUtils.setField(calculator, "pricingPowerPrompt", pricingPowerPrompt);
    }

    @Test
    @DisplayName("Should calculate pricing power successfully")
    void shouldCalculatePricingPowerSuccessfully() {
        String ticker = "AAPL";
        CompanyOverview overview = new CompanyOverview();
        overview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.of(overview));

        SecFiling secFiling = new SecFiling();
        TenKFilings tenK = new TenKFilings();
        tenK.setBusinessDescription("Tech company");
        tenK.setManagementDiscussion("Growing fast");
        tenK.setFiledAt("2023-10-27");
        secFiling.setTenKFilings(List.of(tenK));
        when(secFilingRepository.findBySymbol(ticker)).thenReturn(Optional.of(secFiling));

        IncomeStatementData incomeData = new IncomeStatementData();
        IncomeReport incomeReport = new IncomeReport();
        incomeReport.setDate("2023-09-30");
        incomeReport.setRevenue("383285000000");
        incomeData.setAnnualReports(List.of(incomeReport));
        when(incomeStatementRepository.findBySymbol(ticker)).thenReturn(Optional.of(incomeData));

        FinancialRatiosData ratiosData = new FinancialRatiosData();
        FinancialRatiosReport ratiosReport = new FinancialRatiosReport();
        ratiosReport.setDate("2023-09-30");
        ratiosReport.setGrossProfitMargin(new BigDecimal("0.44"));
        ratiosData.setAnnualReports(List.of(ratiosReport));
        when(financialRatiosRepository.findBySymbol(ticker)).thenReturn(Optional.of(ratiosData));

        when(optionalityCalculator.getLatestEarningsTranscript(ticker)).thenReturn("Latest transcript content");

        String llmResponse = "{\"score\": 4, \"explanation\": \"High pricing power due to brand strength.\"}";
        when(llmService.callLlmWithOllama(any(Prompt.class), eq("pricing_power_analysis"), eq(ticker))).thenReturn(llmResponse);

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("pricingPower");
        assertThat(result.getScore()).isEqualTo(4);
        assertThat(result.getExplanation()).isEqualTo("High pricing power due to brand strength.");
        verify(eventPublisher, atLeastOnce()).publishEvent(any(MessageEvent.class));
    }

    @Test
    @DisplayName("Should handle missing company overview")
    void shouldHandleMissingCompanyOverview() {
        String ticker = "UNKNOWN";
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        verify(eventPublisher).publishEvent(any(ErrorEvent.class));
    }

    @Test
    @DisplayName("Should handle LLM failure")
    void shouldHandleLlmFailure() {
        String ticker = "AAPL";
        CompanyOverview overview = new CompanyOverview();
        overview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.of(overview));
        when(secFilingRepository.findBySymbol(ticker)).thenReturn(Optional.empty());
        IncomeStatementData incomeData = new IncomeStatementData();
        IncomeReport incomeReport = new IncomeReport();
        incomeReport.setDate("2023-09-30");
        incomeReport.setRevenue("1");
        incomeReport.setSellingGeneralAndAdministrativeExpenses("1");
        incomeData.setAnnualReports(List.of(incomeReport));
        when(incomeStatementRepository.findBySymbol(ticker)).thenReturn(Optional.of(incomeData));

        FinancialRatiosData ratiosData = new FinancialRatiosData();
        FinancialRatiosReport ratiosReport = new FinancialRatiosReport();
        ratiosReport.setDate("2023-09-30");
        ratiosReport.setGrossProfitMargin(new BigDecimal("0.44"));
        ratiosReport.setEbitdaMargin(new BigDecimal("0.10"));
        ratiosData.setAnnualReports(List.of(ratiosReport));
        when(financialRatiosRepository.findBySymbol(ticker)).thenReturn(Optional.of(ratiosData));
        when(optionalityCalculator.getLatestEarningsTranscript(ticker)).thenReturn("Transcript");

        when(llmService.callLlmWithOllama(any(Prompt.class), anyString(), anyString())).thenThrow(new RuntimeException("LLM down"));

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
        assertThat(result.getExplanation()).contains("failed");
        verify(eventPublisher, atLeastOnce()).publishEvent(any(ErrorEvent.class));
    }
}
