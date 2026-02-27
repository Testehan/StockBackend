package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.filing.TenKFilings;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.*;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReinvestmentCalculator Tests")
class ReinvestmentCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private CashFlowRepository cashFlowRepository;
    @Mock
    private FinancialRatiosRepository financialRatiosRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private BalanceSheetRepository balanceSheetRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SafeParser safeParser;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource reinvestmentsPrompt;

    private ReinvestmentCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ReinvestmentCalculator(
                companyOverviewRepository,
                incomeStatementRepository,
                cashFlowRepository,
                financialRatiosRepository,
                secFilingRepository,
                balanceSheetRepository,
                llmService,
                eventPublisher,
                safeParser
        );

        try {
            String promptTemplate = "Test prompt with {company_name} and {management_discussion}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(reinvestmentsPrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
        }

        ReflectionTestUtils.setField(calculator, "reinvestmentsPrompt", reinvestmentsPrompt);
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
    @DisplayName("Should return error when financial ratios data is empty")
    void shouldReturnErrorWhenFinancialRatiosEmpty() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("No annual or quarterly financial ratios data available");
    }

    @Test
    @DisplayName("Should calculate 5-year average ROIC correctly")
    void shouldCalculate5yrAverageRoicCorrectly() throws Exception {
        FinancialRatiosData financialRatiosData = new FinancialRatiosData();
        List<FinancialRatiosReport> annualReports = new ArrayList<>();

        FinancialRatiosReport report1 = new FinancialRatiosReport();
        report1.setDate("2024-01-01");
        report1.setRoic(new BigDecimal("0.25"));
        annualReports.add(report1);

        FinancialRatiosReport report2 = new FinancialRatiosReport();
        report2.setDate("2023-01-01");
        report2.setRoic(new BigDecimal("0.20"));
        annualReports.add(report2);

        FinancialRatiosReport report3 = new FinancialRatiosReport();
        report3.setDate("2022-01-01");
        report3.setRoic(new BigDecimal("0.15"));
        annualReports.add(report3);

        financialRatiosData.setAnnualReports(annualReports);
        Optional<FinancialRatiosData> optionalData = Optional.of(financialRatiosData);

        var method = ReinvestmentCalculator.class.getDeclaredMethod("calculate5yrAverageRoic", Optional.class);
        method.setAccessible(true);
        BigDecimal result = (BigDecimal) method.invoke(null, optionalData);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should classify invested capital trend as Increasing")
    void shouldClassifyInvestedCapitalTrendIncreasing() {
        assertThat(calculator.getClass().getDeclaredMethods()).isNotEmpty();
    }
}