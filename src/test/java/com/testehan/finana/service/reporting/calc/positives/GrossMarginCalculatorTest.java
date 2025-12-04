package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.FinancialRatiosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GrossMarginCalculator Tests")
class GrossMarginCalculatorTest {

    @Mock
    private FinancialRatiosRepository financialRatiosRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SseEmitter sseEmitter;

    private GrossMarginCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new GrossMarginCalculator(financialRatiosRepository, eventPublisher);
    }

    private FinancialRatiosReport createGrossMarginReport(String date, BigDecimal grossMargin) {
        FinancialRatiosReport report = new FinancialRatiosReport();
        report.setDate(date);
        report.setGrossProfitMargin(grossMargin);
        return report;
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any());
    }

    // ==================== Insufficient Data Tests ====================

    @Test
    @DisplayName("Should return 0 score when no financial ratios data found")
    void shouldReturnZeroScoreWhenNoFinancialRatiosData() {
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("No quarterly financial ratios data available");
    }

    @Test
    @DisplayName("Should return 0 score when no quarterly reports found")
    void shouldReturnZeroScoreWhenNoQuarterlyReports() {
        FinancialRatiosData data = new FinancialRatiosData();
        data.setQuarterlyReports(new ArrayList<>());
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("No quarterly financial ratios data available");
    }

    @Test
    @DisplayName("Should return 0 score when no gross margin data found in reports")
    void shouldReturnZeroScoreWhenNoGrossMarginData() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createGrossMarginReport("2024-01-01", null));
        data.setQuarterlyReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("No gross profit margin data available");
    }

    // ==================== Score 1 Tests (Gross Margin < 50%) ====================

    @Test
    @DisplayName("Should return score 1 for gross margin less than 50%")
    void shouldReturnScoreOneForLowGrossMargin() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createGrossMarginReport("2024-01-01", new BigDecimal("0.40"))); // 40%
        reports.add(createGrossMarginReport("2023-10-01", new BigDecimal("0.42")));
        reports.add(createGrossMarginReport("2023-07-01", new BigDecimal("0.38")));
        reports.add(createGrossMarginReport("2023-04-01", new BigDecimal("0.40")));
        data.setQuarterlyReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Average = 40% -> Score 1
        assertThat(result.getScore()).isEqualTo(1);
        assertThat(result.getExplanation()).contains("lower profitability");
    }

    // ==================== Score 2 Tests (Gross Margin 50% - 80%) ====================

    @Test
    @DisplayName("Should return score 2 for gross margin between 50% and 80%")
    void shouldReturnScoreTwoForMediumGrossMargin() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createGrossMarginReport("2024-01-01", new BigDecimal("0.60"))); // 60%
        reports.add(createGrossMarginReport("2023-10-01", new BigDecimal("0.58")));
        reports.add(createGrossMarginReport("2023-07-01", new BigDecimal("0.62")));
        reports.add(createGrossMarginReport("2023-04-01", new BigDecimal("0.60")));
        data.setQuarterlyReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Average = 60% -> Score 2
        assertThat(result.getScore()).isEqualTo(2);
        assertThat(result.getExplanation()).contains("healthy profitability");
    }

    // ==================== Score 3 Tests (Gross Margin > 80%) ====================

    @Test
    @DisplayName("Should return score 3 for gross margin greater than 80%")
    void shouldReturnScoreThreeForHighGrossMargin() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createGrossMarginReport("2024-01-01", new BigDecimal("0.85"))); // 85%
        reports.add(createGrossMarginReport("2023-10-01", new BigDecimal("0.83")));
        reports.add(createGrossMarginReport("2023-07-01", new BigDecimal("0.87")));
        reports.add(createGrossMarginReport("2023-04-01", new BigDecimal("0.85")));
        data.setQuarterlyReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Average = 85% -> Score 3
        assertThat(result.getScore()).isEqualTo(3);
        assertThat(result.getExplanation()).contains("very strong profitability");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should calculate average correctly for 4 quarters")
    void shouldCalculateAverageCorrectly() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        // Average = (40 + 50 + 60 + 70) / 4 = 55%
        reports.add(createGrossMarginReport("2024-01-01", new BigDecimal("0.40")));
        reports.add(createGrossMarginReport("2023-10-01", new BigDecimal("0.50")));
        reports.add(createGrossMarginReport("2023-07-01", new BigDecimal("0.60")));
        reports.add(createGrossMarginReport("2023-04-01", new BigDecimal("0.70")));
        data.setQuarterlyReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Average = 55% -> Score 2 (50-80%)
        assertThat(result.getScore()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle less than 4 quarters of data")
    void shouldHandleLessThanFourQuarters() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        // Only 2 quarters available
        reports.add(createGrossMarginReport("2024-01-01", new BigDecimal("0.70")));
        reports.add(createGrossMarginReport("2023-10-01", new BigDecimal("0.75")));
        data.setQuarterlyReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Average = 72.5% -> Score 2 (50-80%)
        assertThat(result.getScore()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should skip null gross margin values when calculating average")
    void shouldSkipNullGrossMarginValues() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        // 2 valid, 2 null -> average of 2
        reports.add(createGrossMarginReport("2024-01-01", new BigDecimal("0.70"))); // 70%
        reports.add(createGrossMarginReport("2023-10-01", null));
        reports.add(createGrossMarginReport("2023-07-01", new BigDecimal("0.90"))); // 90%
        reports.add(createGrossMarginReport("2023-04-01", null));
        data.setQuarterlyReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Average = (70 + 90) / 2 = 80% -> Score 2 (50-80%, inclusive of 80%)
        assertThat(result.getScore()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle boundary value at 50%")
    void shouldHandleBoundaryValueAtFiftyPercent() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createGrossMarginReport("2024-01-01", new BigDecimal("0.50"))); // Exactly 50%
        reports.add(createGrossMarginReport("2023-10-01", new BigDecimal("0.50")));
        reports.add(createGrossMarginReport("2023-07-01", new BigDecimal("0.50")));
        reports.add(createGrossMarginReport("2023-04-01", new BigDecimal("0.50")));
        data.setQuarterlyReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Exactly 50% is <= 80%, so Score 2
        assertThat(result.getScore()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle boundary value at 80%")
    void shouldHandleBoundaryValueAtEightyPercent() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createGrossMarginReport("2024-01-01", new BigDecimal("0.80"))); // Exactly 80%
        reports.add(createGrossMarginReport("2023-10-01", new BigDecimal("0.80")));
        reports.add(createGrossMarginReport("2023-07-01", new BigDecimal("0.80")));
        reports.add(createGrossMarginReport("2023-04-01", new BigDecimal("0.80")));
        data.setQuarterlyReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Exactly 80% is <= 80%, so Score 2
        assertThat(result.getScore()).isEqualTo(2);
    }
}
