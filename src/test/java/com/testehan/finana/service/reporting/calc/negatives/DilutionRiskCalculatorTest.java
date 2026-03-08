package com.testehan.finana.service.reporting.calc.negatives;

import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DilutionRiskCalculator Tests")
class DilutionRiskCalculatorTest {

    @Mock
    private IncomeStatementRepository incomeRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;

    private DilutionRiskCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DilutionRiskCalculator(incomeRepository, eventPublisher);
    }

    @Test
    @DisplayName("Should return -10 when no data is found")
    void shouldReturnMinusTenWhenNoData() {
        when(incomeRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getName()).isEqualTo("extremeDilution");
        assertThat(result.getScore()).isEqualTo(-10);
        verify(eventPublisher).publishEvent(any(ErrorEvent.class));
    }

    @Test
    @DisplayName("Should return 0 when dilution is low (< 3%)")
    void shouldReturnZeroWhenLowDilution() {
        IncomeStatementData data = new IncomeStatementData();
        List<IncomeReport> reports = new ArrayList<>();
        reports.add(createReport("2022-12-31", "1000"));
        reports.add(createReport("2023-12-31", "1020")); // 2% growth
        data.setAnnualReports(reports);

        when(incomeRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("2,00%");
        }

        @Test
        @DisplayName("Should return -2 when dilution is medium (3% - 5%)")
        void shouldReturnMinusTwoWhenMediumDilution() {
        IncomeStatementData data = new IncomeStatementData();
        List<IncomeReport> reports = new ArrayList<>();
        reports.add(createReport("2022-12-31", "1000"));
        reports.add(createReport("2023-12-31", "1040")); // 4% growth
        data.setAnnualReports(reports);

        when(incomeRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-2);
        assertThat(result.getExplanation()).contains("4,00%");
        }

        @Test
        @DisplayName("Should return -4 when dilution is high (> 5%)")
        void shouldReturnMinusFourWhenHighDilution() {
        IncomeStatementData data = new IncomeStatementData();
        List<IncomeReport> reports = new ArrayList<>();
        reports.add(createReport("2022-12-31", "1000"));
        reports.add(createReport("2023-12-31", "1100")); // 10% growth
        data.setAnnualReports(reports);

        when(incomeRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-4);
        assertThat(result.getExplanation()).contains("10,00%");
        }    private IncomeReport createReport(String date, String shares) {
        IncomeReport report = new IncomeReport();
        report.setDate(date);
        report.setWeightedAverageShsOutDil(shares);
        return report;
    }
}
