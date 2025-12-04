package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.EarningsHistory;
import com.testehan.finana.model.QuarterlyEarning;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.EarningsHistoryRepository;
import com.testehan.finana.service.reporting.events.MessageEvent;
import com.testehan.finana.util.SafeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EpsCalculator Tests")
class EpsCalculatorTest {

    @Mock
    private EarningsHistoryRepository earningsHistoryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SafeParser safeParser;
    @Mock
    private SseEmitter sseEmitter;

    private EpsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new EpsCalculator(earningsHistoryRepository, eventPublisher, safeParser);
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any(MessageEvent.class));
    }

    private List<QuarterlyEarning> createQuarterlyEarnings(List<String> epsValues) {
        List<QuarterlyEarning> earnings = new ArrayList<>();
        for (int i = 0; i < epsValues.size(); i++) {
            QuarterlyEarning earning = new QuarterlyEarning();
            earning.setFiscalDateEnding("2024-0" + ((i % 4) + 1) + "-31");
            earning.setReportedEPS(epsValues.get(i));
            earnings.add(earning);
        }
        return earnings;
    }

    @Test
    @DisplayName("Should return score 0 when no earnings history found")
    void shouldReturnZeroWhenNoEarningsHistory() {
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("Insufficient");
    }

    @Test
    @DisplayName("Should return score 0 when quarterly earnings is null")
    void shouldReturnZeroWhenQuarterlyEarningsNull() {
        EarningsHistory history = new EarningsHistory();
        history.setQuarterlyEarnings(null);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("Insufficient");
    }

    @Test
    @DisplayName("Should return score 0 when less than 8 quarters available")
    void shouldReturnZeroWhenLessThanEightQuarters() {
        EarningsHistory history = new EarningsHistory();
        List<QuarterlyEarning> earnings = createQuarterlyEarnings(List.of("1.0", "1.0", "1.0", "1.0", "1.0", "1.0", "1.0"));
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("Insufficient");
    }

    @Test
    @DisplayName("Should return score 0 when EPS is negative")
    void shouldReturnScoreZeroWhenEpsNegative() {
        EarningsHistory history = new EarningsHistory();
        List<QuarterlyEarning> earnings = createQuarterlyEarnings(
            List.of("-1.0", "-1.0", "-1.0", "-1.0", "1.0", "1.0", "1.0", "1.0")
        );
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));
        mockEventPublisher();
        
        when(safeParser.parse(any())).thenReturn(new BigDecimal("-1.0"));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("Negative");
    }

    @Test
    @DisplayName("Should return score 1 when current EPS positive but previous was zero or negative")
    void shouldReturnScoreOneWhenPreviousEpsZeroOrNegative() {
        EarningsHistory history = new EarningsHistory();
        // Last 4 quarters have EPS, earlier 4 have 0
        List<QuarterlyEarning> earnings = createQuarterlyEarnings(
            List.of("1.0", "1.0", "1.0", "1.0", "0.0", "0.0", "0.0", "0.0")
        );
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));
        mockEventPublisher();
        
        // Use lenient to handle any() matchers
        lenient().when(safeParser.parse(any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            if ("0.0".equals(value)) return BigDecimal.ZERO;
            return new BigDecimal(value);
        });

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Previous TTM = 0, Current TTM > 0, so score = 1
        assertThat(result.getScore()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should return score 3 when EPS growth is >= 15%")
    void shouldReturnScoreThreeWhenEpsGrowthFast() {
        EarningsHistory history = new EarningsHistory();
        // Current TTM = 13.0, Previous TTM = 10.0 = 30% growth (definitely > 15%)
        List<QuarterlyEarning> earnings = createQuarterlyEarnings(
            List.of("3.50", "3.50", "3.00", "3.00", "2.50", "2.50", "2.50", "2.50")
        );
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));
        mockEventPublisher();
        
        lenient().when(safeParser.parse(any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return new BigDecimal(value);
        });

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        // Current TTM = 13.0, Previous TTM = 10.0 = 30% growth > 15%, should get score 3
        assertThat(result.getScore()).isGreaterThanOrEqualTo(2);
        assertThat(result.getExplanation()).contains("growing fast");
    }

    @Test
    @DisplayName("Should return score 2 when EPS growth is positive but < 15%")
    void shouldReturnScoreTwoWhenEpsGrowthSlow() {
        EarningsHistory history = new EarningsHistory();
        // Current TTM = 10.5, Previous TTM = 10.0 = 5% growth
        List<QuarterlyEarning> earnings = createQuarterlyEarnings(
            List.of("2.75", "2.75", "2.50", "2.50", "2.50", "2.50", "2.50", "2.50")
        );
        history.setQuarterlyEarnings(earnings);
        when(earningsHistoryRepository.findBySymbol("AAPL")).thenReturn(Optional.of(history));
        mockEventPublisher();
        
        when(safeParser.parse(any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return new BigDecimal(value);
        });

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(2);
        assertThat(result.getExplanation()).contains("stable performance");
    }
}
