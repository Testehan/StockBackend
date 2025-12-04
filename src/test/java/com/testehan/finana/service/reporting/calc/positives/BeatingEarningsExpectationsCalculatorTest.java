package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.EarningsHistory;
import com.testehan.finana.model.QuarterlyEarning;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.EarningsHistoryRepository;
import com.testehan.finana.service.FinancialDataService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BeatingEarningsExpectationsCalculator Tests")
class BeatingEarningsExpectationsCalculatorTest {

    @Mock
    private EarningsHistoryRepository earningsHistoryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;

    private BeatingEarningsExpectationsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new BeatingEarningsExpectationsCalculator(null, earningsHistoryRepository, eventPublisher);
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any(ErrorEvent.class));
    }

    private List<QuarterlyEarning> createQuarterlyEarningsWithSurprise(List<String> surprisePercentages) {
        List<QuarterlyEarning> earnings = new ArrayList<>();
        for (int i = 0; i < surprisePercentages.size(); i++) {
            QuarterlyEarning earning = new QuarterlyEarning();
            earning.setFiscalDateEnding("2024-0" + ((i % 4) + 1) + "-31");
            earning.setReportedEPS("1.0");
            earning.setEstimatedEPS("0.9");
            earning.setSurprisePercentage(surprisePercentages.get(i));
            earnings.add(earning);
        }
        return earnings;
    }

    @Test
    @DisplayName("Should return score 0 when earnings history not found")
    void shouldReturnZeroWhenEarningsHistoryNotFound() {
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return score 0 when quarterly earnings is null")
    void shouldReturnZeroWhenQuarterlyEarningsNull() {
        EarningsHistory history = new EarningsHistory();
        history.setQuarterlyEarnings(null);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));
        mockEventPublisher();

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return score 0 when quarterly earnings is empty")
    void shouldReturnZeroWhenQuarterlyEarningsEmpty() {
        EarningsHistory history = new EarningsHistory();
        history.setQuarterlyEarnings(new ArrayList<>());
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));
        mockEventPublisher();

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should calculate score correctly with all big beats (>50%)")
    void shouldCalculateScoreWithAllBigBeats() {
        EarningsHistory history = new EarningsHistory();
        List<QuarterlyEarning> earnings = createQuarterlyEarningsWithSurprise(
            List.of("60.0", "70.0", "80.0", "55.0")
        );
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(4); // 4 quarters * 1 point each
        assertThat(result.getExplanation()).contains("Big Beats --> 4");
    }

    @Test
    @DisplayName("Should calculate score correctly with all medium beats (0-50%)")
    void shouldCalculateScoreWithAllMediumBeats() {
        EarningsHistory history = new EarningsHistory();
        List<QuarterlyEarning> earnings = createQuarterlyEarningsWithSurprise(
            List.of("10.0", "20.0", "30.0", "40.0")
        );
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(2); // 4 quarters * 0.5 points each
        assertThat(result.getExplanation()).contains("medium beats --> 4");
    }

    @Test
    @DisplayName("Should calculate score correctly with all misses (<0%)")
    void shouldCalculateScoreWithAllMisses() {
        EarningsHistory history = new EarningsHistory();
        List<QuarterlyEarning> earnings = createQuarterlyEarningsWithSurprise(
            List.of("-10.0", "-20.0", "-30.0", "-5.0")
        );
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0); // Minimum is 0, -4 would be capped
        assertThat(result.getExplanation()).contains("misses --> 4");
    }

    @Test
    @DisplayName("Should calculate score correctly with mixed results")
    void shouldCalculateScoreWithMixedResults() {
        EarningsHistory history = new EarningsHistory();
        // 1 big beat (+1), 1 medium beat (+0.5), 2 misses (-2) = 0 (capped)
        List<QuarterlyEarning> earnings = createQuarterlyEarningsWithSurprise(
            List.of("60.0", "25.0", "-10.0", "-5.0")
        );
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return positive score with good mix of beats")
    void shouldReturnPositiveScoreWithGoodMix() {
        EarningsHistory history = new EarningsHistory();
        // 2 big beats (+2), 1 medium beat (+0.5), 1 miss (-1) = 1.5 -> 1 (int)
        List<QuarterlyEarning> earnings = createQuarterlyEarningsWithSurprise(
            List.of("60.0", "55.0", "25.0", "-10.0")
        );
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(1);
    }
}
