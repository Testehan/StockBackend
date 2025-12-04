package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.QuoteService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValuationCalculator Tests")
class ValuationCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private QuoteService quoteService;
    @Mock
    private EarningsHistoryRepository earningsHistoryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SafeParser safeParser;
    @Mock
    private LlmService llmService;
    @Mock
    private FinancialRatiosRepository financialRatiosRepository;
    @Mock
    private EarningsEstimatesRepository earningsEstimatesRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource valuationPrompt;

    private ValuationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ValuationCalculator(
                companyOverviewRepository,
                quoteService,
                earningsHistoryRepository,
                eventPublisher,
                safeParser,
                llmService,
                financialRatiosRepository,
                earningsEstimatesRepository,
                secFilingRepository
        );
    }

    // ==================== calculateCurrentPE Tests ====================

    @Test
    @DisplayName("Should return empty Mono when no stock quote found")
    void shouldReturnEmptyMonoWhenNoStockQuote() {
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.empty());

        Mono<BigDecimal> result = calculator.calculateCurrentPE("AAPL");

        assertThat(result.block()).isNull();
    }

    @Test
    @DisplayName("Should return empty Mono when no earnings history found")
    void shouldReturnEmptyMonoWhenNoEarningsHistory() {
        GlobalQuote quote = new GlobalQuote();
        quote.setAdjOpen("150.00");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        Mono<BigDecimal> result = calculator.calculateCurrentPE("AAPL");

        assertThat(result.block()).isNull();
    }

    @Test
    @DisplayName("Should calculate P/E ratio correctly with valid data")
    void shouldCalculatePERatioCorrectly() {
        GlobalQuote quote = new GlobalQuote();
        quote.setAdjOpen("400.00");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));

        EarningsHistory earningsHistory = new EarningsHistory();
        List<QuarterlyEarning> quarterlyEarnings = new ArrayList<>();
        
        QuarterlyEarning q1 = new QuarterlyEarning();
        q1.setFiscalDateEnding("2024-12-31");
        q1.setReportedEPS("2.50");
        quarterlyEarnings.add(q1);
        
        QuarterlyEarning q2 = new QuarterlyEarning();
        q2.setFiscalDateEnding("2024-09-30");
        q2.setReportedEPS("2.25");
        quarterlyEarnings.add(q2);
        
        QuarterlyEarning q3 = new QuarterlyEarning();
        q3.setFiscalDateEnding("2024-06-30");
        q3.setReportedEPS("2.00");
        quarterlyEarnings.add(q3);
        
        QuarterlyEarning q4 = new QuarterlyEarning();
        q4.setFiscalDateEnding("2024-03-31");
        q4.setReportedEPS("1.75");
        quarterlyEarnings.add(q4);
        
        earningsHistory.setQuarterlyEarnings(quarterlyEarnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(earningsHistory));
        when(safeParser.parse("2.50")).thenReturn(new BigDecimal("2.50"));
        when(safeParser.parse("2.25")).thenReturn(new BigDecimal("2.25"));
        when(safeParser.parse("2.00")).thenReturn(new BigDecimal("2.00"));
        when(safeParser.parse("1.75")).thenReturn(new BigDecimal("1.75"));

        Mono<BigDecimal> result = calculator.calculateCurrentPE("AAPL");

        BigDecimal peRatio = result.block();
        assertThat(peRatio).isNotNull();
        // P/E = 400 / (2.5 + 2.25 + 2.0 + 1.75) = 400 / 8.5 = 47.06
        assertThat(peRatio).isEqualByComparingTo(new BigDecimal("47.06"));
    }

    @Test
    @DisplayName("Should return empty Mono when less than 4 quarters available")
    void shouldReturnEmptyMonoWhenLessThanFourQuarters() {
        GlobalQuote quote = new GlobalQuote();
        quote.setAdjOpen("150.00");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));

        EarningsHistory earningsHistory = new EarningsHistory();
        List<QuarterlyEarning> quarterlyEarnings = new ArrayList<>();
        
        QuarterlyEarning q1 = new QuarterlyEarning();
        q1.setFiscalDateEnding("2024-12-31");
        q1.setReportedEPS("2.50");
        quarterlyEarnings.add(q1);
        
        QuarterlyEarning q2 = new QuarterlyEarning();
        q2.setFiscalDateEnding("2024-09-30");
        q2.setReportedEPS("2.25");
        quarterlyEarnings.add(q2);
        
        earningsHistory.setQuarterlyEarnings(quarterlyEarnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(earningsHistory));

        Mono<BigDecimal> result = calculator.calculateCurrentPE("AAPL");

        assertThat(result.block()).isNull();
    }

    @Test
    @DisplayName("Should return empty Mono when TTM EPS is zero or negative")
    void shouldReturnEmptyMonoWhenTTMEPSIsZero() {
        GlobalQuote quote = new GlobalQuote();
        quote.setAdjOpen("150.00");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));

        EarningsHistory earningsHistory = new EarningsHistory();
        List<QuarterlyEarning> quarterlyEarnings = new ArrayList<>();
        
        QuarterlyEarning q1 = new QuarterlyEarning();
        q1.setFiscalDateEnding("2024-12-31");
        q1.setReportedEPS("0.00");
        quarterlyEarnings.add(q1);
        
        QuarterlyEarning q2 = new QuarterlyEarning();
        q2.setFiscalDateEnding("2024-09-30");
        q2.setReportedEPS("0.00");
        quarterlyEarnings.add(q2);
        
        QuarterlyEarning q3 = new QuarterlyEarning();
        q3.setFiscalDateEnding("2024-06-30");
        q3.setReportedEPS("0.00");
        quarterlyEarnings.add(q3);
        
        QuarterlyEarning q4 = new QuarterlyEarning();
        q4.setFiscalDateEnding("2024-03-31");
        q4.setReportedEPS("0.00");
        quarterlyEarnings.add(q4);
        
        earningsHistory.setQuarterlyEarnings(quarterlyEarnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(earningsHistory));
        when(safeParser.parse(anyString())).thenReturn(BigDecimal.ZERO);

        Mono<BigDecimal> result = calculator.calculateCurrentPE("AAPL");

        assertThat(result.block()).isNull();
    }

    // ==================== calculateMedianPeRatio Tests ====================

    @Test
    @DisplayName("Should return null when no financial ratios data found")
    void shouldReturnNullWhenNoFinancialRatiosData() {
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        BigDecimal result = calculator.calculateMedianPeRatio("AAPL");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null when no annual reports found")
    void shouldReturnNullWhenNoAnnualReports() {
        FinancialRatiosData data = new FinancialRatiosData();
        data.setAnnualReports(new ArrayList<>());
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        BigDecimal result = calculator.calculateMedianPeRatio("AAPL");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null when no valid P/E ratios found")
    void shouldReturnNullWhenNoValidPERatios() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        
        FinancialRatiosReport report1 = new FinancialRatiosReport();
        report1.setDate("2024-01-01");
        report1.setPeRatio(null);
        reports.add(report1);
        
        FinancialRatiosReport report2 = new FinancialRatiosReport();
        report2.setDate("2023-01-01");
        report2.setPeRatio(BigDecimal.ZERO);
        reports.add(report2);
        
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        BigDecimal result = calculator.calculateMedianPeRatio("AAPL");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should calculate median P/E ratio correctly for odd number of values")
    void shouldCalculateMedianCorrectlyForOddCount() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        
        FinancialRatiosReport report1 = new FinancialRatiosReport();
        report1.setDate("2024-01-01");
        report1.setPeRatio(new BigDecimal("25.00"));
        reports.add(report1);
        
        FinancialRatiosReport report2 = new FinancialRatiosReport();
        report2.setDate("2023-01-01");
        report2.setPeRatio(new BigDecimal("20.00"));
        reports.add(report2);
        
        FinancialRatiosReport report3 = new FinancialRatiosReport();
        report3.setDate("2022-01-01");
        report3.setPeRatio(new BigDecimal("30.00"));
        reports.add(report3);
        
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        BigDecimal result = calculator.calculateMedianPeRatio("AAPL");

        assertThat(result).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("Should calculate median P/E ratio correctly for even number of values")
    void shouldCalculateMedianCorrectlyForEvenCount() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        
        FinancialRatiosReport report1 = new FinancialRatiosReport();
        report1.setDate("2024-01-01");
        report1.setPeRatio(new BigDecimal("25.00"));
        reports.add(report1);
        
        FinancialRatiosReport report2 = new FinancialRatiosReport();
        report2.setDate("2023-01-01");
        report2.setPeRatio(new BigDecimal("20.00"));
        reports.add(report2);
        
        FinancialRatiosReport report3 = new FinancialRatiosReport();
        report3.setDate("2022-01-01");
        report3.setPeRatio(new BigDecimal("30.00"));
        reports.add(report3);
        
        FinancialRatiosReport report4 = new FinancialRatiosReport();
        report4.setDate("2021-01-01");
        report4.setPeRatio(new BigDecimal("15.00"));
        reports.add(report4);
        
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        BigDecimal result = calculator.calculateMedianPeRatio("AAPL");

        assertThat(result).isEqualByComparingTo(new BigDecimal("22.50"));
    }

    // ==================== calculate1YrForwardEpsGrowth Tests ====================

    @Test
    @DisplayName("Should return null when no earnings estimates found")
    void shouldReturnNullWhenNoEarningsEstimates() {
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        BigDecimal result = calculator.calculate1YrForwardEpsGrowth("AAPL");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null when not enough estimates (less than 2)")
    void shouldReturnNullWhenNotEnoughEstimates() {
        EarningsEstimate estimate = new EarningsEstimate();
        List<Estimate> estimates = new ArrayList<>();
        estimate.setEstimates(estimates);
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.of(estimate));

        BigDecimal result = calculator.calculate1YrForwardEpsGrowth("AAPL");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null when current year estimate not found")
    void shouldReturnNullWhenCurrentYearEstimateNotFound() {
        EarningsEstimate estimate = new EarningsEstimate();
        List<Estimate> estimates = new ArrayList<>();
        
        Estimate nextYear = new Estimate();
        nextYear.setDate(String.valueOf(java.time.LocalDate.now().getYear() + 1) + "-01-01");
        nextYear.setEpsAvg("5.00");
        estimates.add(nextYear);
        
        estimate.setEstimates(estimates);
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.of(estimate));

        BigDecimal result = calculator.calculate1YrForwardEpsGrowth("AAPL");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should calculate 1-year forward EPS growth correctly")
    void shouldCalculate1YrForwardEpsGrowthCorrectly() {
        int currentYear = java.time.LocalDate.now().getYear();
        
        EarningsEstimate estimate = new EarningsEstimate();
        List<Estimate> estimates = new ArrayList<>();
        
        Estimate currentYearEst = new Estimate();
        currentYearEst.setDate(currentYear + "-01-01");
        currentYearEst.setEpsAvg("4.00");
        estimates.add(currentYearEst);
        
        Estimate nextYearEst = new Estimate();
        nextYearEst.setDate((currentYear + 1) + "-01-01");
        nextYearEst.setEpsAvg("5.00");
        estimates.add(nextYearEst);
        
        estimate.setEstimates(estimates);
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.of(estimate));
        when(safeParser.parse("4.00")).thenReturn(new BigDecimal("4.00"));
        when(safeParser.parse("5.00")).thenReturn(new BigDecimal("5.00"));

        BigDecimal result = calculator.calculate1YrForwardEpsGrowth("AAPL");

        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("25.0000"));
    }

    @Test
    @DisplayName("Should return null when current EPS is zero")
    void shouldReturnNullWhenCurrentEPSIsZero() {
        int currentYear = java.time.LocalDate.now().getYear();
        
        EarningsEstimate estimate = new EarningsEstimate();
        List<Estimate> estimates = new ArrayList<>();
        
        Estimate currentYearEst = new Estimate();
        currentYearEst.setDate(currentYear + "-01-01");
        currentYearEst.setEpsAvg("0.00");
        estimates.add(currentYearEst);
        
        Estimate nextYearEst = new Estimate();
        nextYearEst.setDate((currentYear + 1) + "-01-01");
        nextYearEst.setEpsAvg("5.00");
        estimates.add(nextYearEst);
        
        estimate.setEstimates(estimates);
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.of(estimate));
        when(safeParser.parse("0.00")).thenReturn(BigDecimal.ZERO);
        when(safeParser.parse("5.00")).thenReturn(new BigDecimal("5.00"));

        BigDecimal result = calculator.calculate1YrForwardEpsGrowth("AAPL");

        assertThat(result).isNull();
    }

    // ==================== calculate3YrForwardEpsGrowth Tests ====================

    @Test
    @DisplayName("Should return null when not enough estimates for 3-year growth")
    void shouldReturnNullWhenNotEnoughEstimatesFor3Yr() {
        EarningsEstimate estimate = new EarningsEstimate();
        List<Estimate> estimates = new ArrayList<>();
        
        Estimate currentYearEst = new Estimate();
        currentYearEst.setDate(java.time.LocalDate.now().getYear() + "-01-01");
        currentYearEst.setEpsAvg("4.00");
        estimates.add(currentYearEst);
        
        Estimate nextYearEst = new Estimate();
        nextYearEst.setDate((java.time.LocalDate.now().getYear() + 1) + "-01-01");
        nextYearEst.setEpsAvg("5.00");
        estimates.add(nextYearEst);
        
        estimate.setEstimates(estimates);
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.of(estimate));

        BigDecimal result = calculator.calculate3YrForwardEpsGrowth("AAPL");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should calculate 3-year forward EPS CAGR correctly")
    void shouldCalculate3YrForwardEpsGrowthCorrectly() {
        int currentYear = java.time.LocalDate.now().getYear();
        
        EarningsEstimate estimate = new EarningsEstimate();
        List<Estimate> estimates = new ArrayList<>();
        
        // Add 4 estimates - needed for the calculation
        Estimate year1Est = new Estimate();
        year1Est.setDate((currentYear - 2) + "-01-01");
        year1Est.setEpsAvg("3.00");
        estimates.add(year1Est);
        
        Estimate year2Est = new Estimate();
        year2Est.setDate((currentYear - 1) + "-01-01");
        year2Est.setEpsAvg("3.50");
        estimates.add(year2Est);
        
        Estimate currentYearEst = new Estimate();
        currentYearEst.setDate(currentYear + "-01-01");
        currentYearEst.setEpsAvg("4.00");
        estimates.add(currentYearEst);
        
        Estimate year3Est = new Estimate();
        year3Est.setDate((currentYear + 3) + "-01-01");
        year3Est.setEpsAvg("6.40");
        estimates.add(year3Est);
        
        estimate.setEstimates(estimates);
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.of(estimate));
        when(safeParser.parse("4.00")).thenReturn(new BigDecimal("4.00"));
        when(safeParser.parse("6.40")).thenReturn(new BigDecimal("6.40"));

        BigDecimal result = calculator.calculate3YrForwardEpsGrowth("AAPL");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should return null when beginning EPS is zero or negative")
    void shouldReturnNullWhenBeginningEPSIsZero() {
        int currentYear = java.time.LocalDate.now().getYear();
        
        EarningsEstimate estimate = new EarningsEstimate();
        List<Estimate> estimates = new ArrayList<>();
        
        // Need at least 4 estimates to pass the initial check
        Estimate year1Est = new Estimate();
        year1Est.setDate((currentYear - 2) + "-01-01");
        year1Est.setEpsAvg("3.00");
        estimates.add(year1Est);
        
        Estimate year2Est = new Estimate();
        year2Est.setDate((currentYear - 1) + "-01-01");
        year2Est.setEpsAvg("3.50");
        estimates.add(year2Est);
        
        Estimate currentYearEst = new Estimate();
        currentYearEst.setDate(currentYear + "-01-01");
        currentYearEst.setEpsAvg("0.00");
        estimates.add(currentYearEst);
        
        Estimate year3Est = new Estimate();
        year3Est.setDate((currentYear + 3) + "-01-01");
        year3Est.setEpsAvg("6.40");
        estimates.add(year3Est);
        
        estimate.setEstimates(estimates);
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.of(estimate));
        when(safeParser.parse("0.00")).thenReturn(BigDecimal.ZERO);

        BigDecimal result = calculator.calculate3YrForwardEpsGrowth("AAPL");

        assertThat(result).isNull();
    }

    // ==================== calculatePegRatio Tests ====================

    @Test
    @DisplayName("Should return empty Mono when P/E calculation fails")
    void shouldReturnEmptyMonoWhenPECalculationFails() {
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.empty());

        Mono<BigDecimal> result = calculator.calculatePegRatio("AAPL");

        assertThat(result.block()).isNull();
    }

    @Test
    @DisplayName("Should return empty Mono when growth rate is null")
    void shouldReturnEmptyMonoWhenGrowthRateIsNull() {
        GlobalQuote quote = new GlobalQuote();
        quote.setAdjOpen("400.00");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));

        EarningsHistory earningsHistory = new EarningsHistory();
        List<QuarterlyEarning> quarterlyEarnings = new ArrayList<>();
        
        QuarterlyEarning q1 = new QuarterlyEarning();
        q1.setFiscalDateEnding("2024-12-31");
        q1.setReportedEPS("2.50");
        quarterlyEarnings.add(q1);
        
        QuarterlyEarning q2 = new QuarterlyEarning();
        q2.setFiscalDateEnding("2024-09-30");
        q2.setReportedEPS("2.25");
        quarterlyEarnings.add(q2);
        
        QuarterlyEarning q3 = new QuarterlyEarning();
        q3.setFiscalDateEnding("2024-06-30");
        q3.setReportedEPS("2.00");
        quarterlyEarnings.add(q3);
        
        QuarterlyEarning q4 = new QuarterlyEarning();
        q4.setFiscalDateEnding("2024-03-31");
        q4.setReportedEPS("1.75");
        quarterlyEarnings.add(q4);
        
        earningsHistory.setQuarterlyEarnings(quarterlyEarnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(earningsHistory));
        when(safeParser.parse("2.50")).thenReturn(new BigDecimal("2.50"));
        when(safeParser.parse("2.25")).thenReturn(new BigDecimal("2.25"));
        when(safeParser.parse("2.00")).thenReturn(new BigDecimal("2.00"));
        when(safeParser.parse("1.75")).thenReturn(new BigDecimal("1.75"));
        
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        Mono<BigDecimal> result = calculator.calculatePegRatio("AAPL");

        assertThat(result.block()).isNull();
    }

    @Test
    @DisplayName("Should return empty Mono when growth rate is zero or negative")
    void shouldReturnEmptyMonoWhenGrowthRateIsZero() {
        GlobalQuote quote = new GlobalQuote();
        quote.setAdjOpen("400.00");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));

        EarningsHistory earningsHistory = new EarningsHistory();
        List<QuarterlyEarning> quarterlyEarnings = new ArrayList<>();
        
        QuarterlyEarning q1 = new QuarterlyEarning();
        q1.setFiscalDateEnding("2024-12-31");
        q1.setReportedEPS("2.50");
        quarterlyEarnings.add(q1);
        
        QuarterlyEarning q2 = new QuarterlyEarning();
        q2.setFiscalDateEnding("2024-09-30");
        q2.setReportedEPS("2.25");
        quarterlyEarnings.add(q2);
        
        QuarterlyEarning q3 = new QuarterlyEarning();
        q3.setFiscalDateEnding("2024-06-30");
        q3.setReportedEPS("2.00");
        quarterlyEarnings.add(q3);
        
        QuarterlyEarning q4 = new QuarterlyEarning();
        q4.setFiscalDateEnding("2024-03-31");
        q4.setReportedEPS("1.75");
        quarterlyEarnings.add(q4);
        
        earningsHistory.setQuarterlyEarnings(quarterlyEarnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(earningsHistory));
        when(safeParser.parse("2.50")).thenReturn(new BigDecimal("2.50"));
        when(safeParser.parse("2.25")).thenReturn(new BigDecimal("2.25"));
        when(safeParser.parse("2.00")).thenReturn(new BigDecimal("2.00"));
        when(safeParser.parse("1.75")).thenReturn(new BigDecimal("1.75"));
        
        int currentYear = java.time.LocalDate.now().getYear();
        EarningsEstimate estimate = new EarningsEstimate();
        List<Estimate> estimates = new ArrayList<>();
        
        Estimate currentYearEst = new Estimate();
        currentYearEst.setDate(currentYear + "-01-01");
        currentYearEst.setEpsAvg("4.00");
        estimates.add(currentYearEst);
        
        Estimate nextYearEst = new Estimate();
        nextYearEst.setDate((currentYear + 1) + "-01-01");
        nextYearEst.setEpsAvg("4.00");
        estimates.add(nextYearEst);
        
        estimate.setEstimates(estimates);
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.of(estimate));
        when(safeParser.parse("4.00")).thenReturn(new BigDecimal("4.00"));

        Mono<BigDecimal> result = calculator.calculatePegRatio("AAPL");

        assertThat(result.block()).isNull();
    }

    @Test
    @DisplayName("Should calculate PEG ratio correctly")
    void shouldCalculatePegRatioCorrectly() {
        GlobalQuote quote = new GlobalQuote();
        quote.setAdjOpen("400.00");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));

        EarningsHistory earningsHistory = new EarningsHistory();
        List<QuarterlyEarning> quarterlyEarnings = new ArrayList<>();
        
        QuarterlyEarning q1 = new QuarterlyEarning();
        q1.setFiscalDateEnding("2024-12-31");
        q1.setReportedEPS("10.00");
        quarterlyEarnings.add(q1);
        
        QuarterlyEarning q2 = new QuarterlyEarning();
        q2.setFiscalDateEnding("2024-09-30");
        q2.setReportedEPS("10.00");
        quarterlyEarnings.add(q2);
        
        QuarterlyEarning q3 = new QuarterlyEarning();
        q3.setFiscalDateEnding("2024-06-30");
        q3.setReportedEPS("10.00");
        quarterlyEarnings.add(q3);
        
        QuarterlyEarning q4 = new QuarterlyEarning();
        q4.setFiscalDateEnding("2024-03-31");
        q4.setReportedEPS("10.00");
        quarterlyEarnings.add(q4);
        
        earningsHistory.setQuarterlyEarnings(quarterlyEarnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(earningsHistory));
        when(safeParser.parse("10.00")).thenReturn(new BigDecimal("10.00"));
        
        int currentYear = java.time.LocalDate.now().getYear();
        EarningsEstimate estimate = new EarningsEstimate();
        List<Estimate> estimates = new ArrayList<>();
        
        Estimate currentYearEst = new Estimate();
        currentYearEst.setDate(currentYear + "-01-01");
        currentYearEst.setEpsAvg("40.00");
        estimates.add(currentYearEst);
        
        Estimate nextYearEst = new Estimate();
        nextYearEst.setDate((currentYear + 1) + "-01-01");
        nextYearEst.setEpsAvg("50.00");
        estimates.add(nextYearEst);
        
        estimate.setEstimates(estimates);
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.of(estimate));
        when(safeParser.parse("40.00")).thenReturn(new BigDecimal("40.00"));
        when(safeParser.parse("50.00")).thenReturn(new BigDecimal("50.00"));

        Mono<BigDecimal> result = calculator.calculatePegRatio("AAPL");

        BigDecimal pegRatio = result.block();
        assertThat(pegRatio).isNotNull();
        // PEG = P/E / Growth = 10 / 25 = 0.4
        assertThat(pegRatio).isEqualByComparingTo(new BigDecimal("0.40"));
    }
}
