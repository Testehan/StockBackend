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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoicCalculator Tests")
class RoicCalculatorTest {

    @Mock
    private FinancialRatiosRepository financialRatiosRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SseEmitter sseEmitter;

    private RoicCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RoicCalculator(financialRatiosRepository, eventPublisher);
    }

    private FinancialRatiosReport createRoicReport(String date, BigDecimal roic) {
        FinancialRatiosReport report = new FinancialRatiosReport();
        report.setDate(date);
        report.setRoic(roic);
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
        assertThat(result.getExplanation()).contains("No annual financial ratios data available");
    }

    @Test
    @DisplayName("Should return 0 score when no annual reports found")
    void shouldReturnZeroScoreWhenNoAnnualReports() {
        FinancialRatiosData data = new FinancialRatiosData();
        data.setAnnualReports(new ArrayList<>());
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("No annual financial ratios data available");
    }

    @Test
    @DisplayName("Should return 0 score when no ROIC data found")
    void shouldReturnZeroScoreWhenNoRoicData() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createRoicReport("2024-01-01", null));
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("No annual ROIC data available");
    }

    // ==================== Score 0 Tests (ROIC < 8%) ====================

    @Test
    @DisplayName("Should return score 0 for ROIC less than 8%")
    void shouldReturnScoreZeroForLowRoic() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createRoicReport("2024-01-01", new BigDecimal("0.05"))); // 5%
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("destroying value");
    }

    // ==================== Score 1 Tests (ROIC 8% - 12%) ====================

    @Test
    @DisplayName("Should return score 1 for ROIC between 8% and 12%")
    void shouldReturnScoreOneForMediumRoic() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createRoicReport("2024-01-01", new BigDecimal("0.10"))); // 10%
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(1);
        assertThat(result.getExplanation()).contains("breaking even");
    }

    // ==================== Score 2 Tests (ROIC 12% - 20%) ====================

    @Test
    @DisplayName("Should return score 2 for ROIC between 12% and 20%")
    void shouldReturnScoreTwoForGoodRoic() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createRoicReport("2024-01-01", new BigDecimal("0.15"))); // 15%
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(2);
        assertThat(result.getExplanation()).contains("solid compounder");
    }

    // ==================== Score 3 Tests (ROIC > 20%) ====================

    @Test
    @DisplayName("Should return score 3 for ROIC greater than 20%")
    void shouldReturnScoreThreeForHighRoic() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createRoicReport("2024-01-01", new BigDecimal("0.25"))); // 25%
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(3);
        assertThat(result.getExplanation()).contains("strong competitive advantage");
    }

    // ==================== Rising Rule Tests ====================

    @Test
    @DisplayName("Should increase score by 1 when ROIC is rising above median (with margin)")
    void shouldApplyRisingRuleWhenRoicIsRising() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        // Latest ROIC = 13% (would be score 2)
        // Historical: 10%, 9%, 8%, 7% -> median = 8.5%
        // 13% > 8.5% + 1% = 9.5%, so rising rule applies -> score becomes 3
        reports.add(createRoicReport("2024-01-01", new BigDecimal("0.13"))); // Latest
        reports.add(createRoicReport("2023-01-01", new BigDecimal("0.10")));
        reports.add(createRoicReport("2022-01-01", new BigDecimal("0.09")));
        reports.add(createRoicReport("2021-01-01", new BigDecimal("0.08")));
        reports.add(createRoicReport("2020-01-01", new BigDecimal("0.07")));
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Base score would be 2 (12-20%), plus 1 for rising rule = 3
        assertThat(result.getScore()).isEqualTo(3);
        assertThat(result.getExplanation()).contains("rising");
    }

    @Test
    @DisplayName("Should not increase score when ROIC is below median (with margin)")
    void shouldNotApplyRisingRuleWhenRoicIsFalling() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        // Latest ROIC = 13% (would be score 2)
        // Historical: 20%, 18%, 16%, 14% -> median = 17%
        // 13% < 17% + 1% = 18%, so rising rule does not apply
        reports.add(createRoicReport("2024-01-01", new BigDecimal("0.13"))); // Latest
        reports.add(createRoicReport("2023-01-01", new BigDecimal("0.20")));
        reports.add(createRoicReport("2022-01-01", new BigDecimal("0.18")));
        reports.add(createRoicReport("2021-01-01", new BigDecimal("0.16")));
        reports.add(createRoicReport("2020-01-01", new BigDecimal("0.14")));
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Base score is 2, no rising rule applied
        assertThat(result.getScore()).isEqualTo(2);
        assertThat(result.getExplanation()).doesNotContain("rising");
    }

    @Test
    @DisplayName("Should not increase score beyond 3 even if rising rule applies")
    void shouldNotExceedMaxScoreThree() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        // Latest ROIC = 25% (already max score 3)
        // Historical: 10%, 9%, 8%, 7% -> median = 8.5%
        // Rising rule would try to add 1, but max is 3
        reports.add(createRoicReport("2024-01-01", new BigDecimal("0.25"))); // Latest
        reports.add(createRoicReport("2023-01-01", new BigDecimal("0.10")));
        reports.add(createRoicReport("2022-01-01", new BigDecimal("0.09")));
        reports.add(createRoicReport("2021-01-01", new BigDecimal("0.08")));
        reports.add(createRoicReport("2020-01-01", new BigDecimal("0.07")));
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Max score is 3
        assertThat(result.getScore()).isEqualTo(3);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle single year of ROIC data")
    void shouldHandleSingleYearOfData() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        reports.add(createRoicReport("2024-01-01", new BigDecimal("0.15"))); // 15%
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Single year - latest and median are the same
        assertThat(result.getScore()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle even number of years for median calculation")
    void shouldHandleEvenNumberOfYears() {
        FinancialRatiosData data = new FinancialRatiosData();
        List<FinancialRatiosReport> reports = new ArrayList<>();
        // 4 years: 8%, 10%, 12%, 14% -> median = (10+12)/2 = 11%
        reports.add(createRoicReport("2024-01-01", new BigDecimal("0.13")));
        reports.add(createRoicReport("2023-01-01", new BigDecimal("0.14")));
        reports.add(createRoicReport("2022-01-01", new BigDecimal("0.10")));
        reports.add(createRoicReport("2021-01-01", new BigDecimal("0.08")));
        data.setAnnualReports(reports);
        when(financialRatiosRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Latest = 13% (12-20% -> score 2)
        // Median of 4 values = (10+12)/2/100 = 11% = 0.11
        // 13% > 11% + 1% = 12% -> rising rule applies -> score 3
        assertThat(result.getScore()).isEqualTo(3);
    }
}
