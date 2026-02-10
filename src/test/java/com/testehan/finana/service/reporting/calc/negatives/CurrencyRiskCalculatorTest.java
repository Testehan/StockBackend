package com.testehan.finana.service.reporting.calc.negatives;

import com.testehan.finana.model.finstatement.RevenueGeographicSegmentationData;
import com.testehan.finana.model.finstatement.RevenueGeographicSegmentationReport;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.RevenueGeographicSegmentationRepository;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyRiskCalculator Tests")
class CurrencyRiskCalculatorTest {

    @Mock
    private RevenueGeographicSegmentationRepository revenueRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;

    private CurrencyRiskCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CurrencyRiskCalculator(revenueRepository, eventPublisher);
    }

    @Test
    @DisplayName("Should return -10 when no data is found")
    void shouldReturnMinusTenWhenNoData() {
        when(revenueRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getName()).isEqualTo("currencyRisk");
        assertThat(result.getScore()).isEqualTo(-10);
        verify(eventPublisher).publishEvent(any(ErrorEvent.class));
    }

    @Test
    @DisplayName("Should return -2 when domestic key cannot be identified")
    void shouldReturnMinusTwoWhenDomesticKeyNotFound() {
        RevenueGeographicSegmentationData data = new RevenueGeographicSegmentationData();
        RevenueGeographicSegmentationReport report = new RevenueGeographicSegmentationReport();
        Map<String, String> segments = new HashMap<>();
        segments.put("Europe", "1000");
        segments.put("Asia", "1000");
        report.setData(segments);
        report.setDate("2023-12-31");
        data.setReports(List.of(report));

        when(revenueRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-2);
        assertThat(result.getExplanation()).contains("The company gets revenue from these areas");
    }

    @Test
    @DisplayName("Should return -2 when foreign revenue > 75%")
    void shouldReturnMinusTwoWhenHighForeignRevenue() {
        RevenueGeographicSegmentationData data = new RevenueGeographicSegmentationData();
        RevenueGeographicSegmentationReport report = new RevenueGeographicSegmentationReport();
        Map<String, String> segments = new HashMap<>();
        segments.put("United States", "20");
        segments.put("Europe", "80");
        report.setData(segments);
        report.setDate("2023-12-31");
        data.setReports(List.of(report));

        when(revenueRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-2);
    }

    @Test
    @DisplayName("Should return -1 when foreign revenue > 50%")
    void shouldReturnMinusOneWhenMediumForeignRevenue() {
        RevenueGeographicSegmentationData data = new RevenueGeographicSegmentationData();
        RevenueGeographicSegmentationReport report = new RevenueGeographicSegmentationReport();
        Map<String, String> segments = new HashMap<>();
        segments.put("USA", "40");
        segments.put("Rest of World", "60");
        report.setData(segments);
        report.setDate("2023-12-31");
        data.setReports(List.of(report));

        when(revenueRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Should return 0 when foreign revenue <= 50%")
    void shouldReturnZeroWhenLowForeignRevenue() {
        RevenueGeographicSegmentationData data = new RevenueGeographicSegmentationData();
        RevenueGeographicSegmentationReport report = new RevenueGeographicSegmentationReport();
        Map<String, String> segments = new HashMap<>();
        segments.put("United States", "60");
        segments.put("International", "40");
        report.setData(segments);
        report.setDate("2023-12-31");
        data.setReports(List.of(report));

        when(revenueRepository.findBySymbol("AAPL")).thenReturn(Optional.of(data));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
    }
}
