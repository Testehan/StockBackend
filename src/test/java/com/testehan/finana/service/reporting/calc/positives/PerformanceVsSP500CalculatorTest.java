package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.quote.IndexData;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.service.QuoteService;
import com.testehan.finana.service.reporting.events.ErrorEvent;
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
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PerformanceVsSP500Calculator Tests")
class PerformanceVsSP500CalculatorTest {

    @Mock
    private QuoteService quoteService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;

    private PerformanceVsSP500Calculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PerformanceVsSP500Calculator(quoteService, eventPublisher);
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any(ErrorEvent.class));
    }

    private GlobalQuote createStockQuote(String date, String adjClose) {
        GlobalQuote quote = new GlobalQuote();
        quote.setDate(date);
        quote.setAdjClose(adjClose);
        return quote;
    }

    private IndexData createIndexQuote(double price) {
        IndexData indexData = new IndexData();
        indexData.setPrice(price);
        return indexData;
    }

    @Test
    @DisplayName("Should return score 0 when stock price data not available")
    void shouldReturnZeroWhenStockDataNotAvailable() {
        when(quoteService.getFirstStockQuote("AAPL")).thenReturn(Optional.empty());
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.empty());
        mockEventPublisher();

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("Stock price data not available");
    }

    @Test
    @DisplayName("Should return score 0 when S&P 500 index data not available")
    void shouldReturnZeroWhenIndexDataNotAvailable() {
        when(quoteService.getFirstStockQuote("AAPL")).thenReturn(Optional.of(createStockQuote("2020-01-01", "100.0")));
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(createStockQuote("2024-01-01", "150.0")));
        when(quoteService.getIndexQuoteByDate(eq("^GSPC"), any())).thenReturn(Optional.empty());
        mockEventPublisher();

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("S&P 500");
    }

    @Test
    @DisplayName("Should return score 1 when stock outperforms by 0-25%")
    void shouldReturnScoreOneWhenOutperformanceSmall() {
        when(quoteService.getFirstStockQuote("AAPL")).thenReturn(Optional.of(createStockQuote("2020-01-01", "100.0")));
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(createStockQuote("2024-01-01", "120.0")));
        when(quoteService.getIndexQuoteByDate(eq("^GSPC"), any())).thenReturn(Optional.of(createIndexQuote(100.0)), Optional.of(createIndexQuote(110.0)));
        mockEventPublisher();

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return score 2 when stock outperforms by 25-50%")
    void shouldReturnScoreTwoWhenOutperformanceMedium() {
        when(quoteService.getFirstStockQuote("AAPL")).thenReturn(Optional.of(createStockQuote("2020-01-01", "100.0")));
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(createStockQuote("2024-01-01", "150.0")));
        when(quoteService.getIndexQuoteByDate(eq("^GSPC"), any())).thenReturn(Optional.of(createIndexQuote(100.0)), Optional.of(createIndexQuote(110.0)));
        mockEventPublisher();

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return score 3 when stock outperforms by 50-100%")
    void shouldReturnScoreThreeWhenOutperformanceHigh() {
        when(quoteService.getFirstStockQuote("AAPL")).thenReturn(Optional.of(createStockQuote("2020-01-01", "100.0")));
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(createStockQuote("2024-01-01", "200.0")));
        when(quoteService.getIndexQuoteByDate(eq("^GSPC"), any())).thenReturn(Optional.of(createIndexQuote(100.0)), Optional.of(createIndexQuote(110.0)));
        mockEventPublisher();

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should return score 4 when stock outperforms by > 100%")
    void shouldReturnScoreFourWhenOutperformanceVeryHigh() {
        when(quoteService.getFirstStockQuote("AAPL")).thenReturn(Optional.of(createStockQuote("2020-01-01", "100.0")));
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(createStockQuote("2024-01-01", "300.0")));
        when(quoteService.getIndexQuoteByDate(eq("^GSPC"), any())).thenReturn(Optional.of(createIndexQuote(100.0)), Optional.of(createIndexQuote(110.0)));
        mockEventPublisher();

        ReportItem result = calculator.calculateUpsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should return negative score when stock underperforms in calculateDownsidePerformance")
    void shouldReturnNegativeScoreWhenUnderperformance() {
        when(quoteService.getFirstStockQuote("AAPL")).thenReturn(Optional.of(createStockQuote("2020-01-01", "100.0")));
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(createStockQuote("2024-01-01", "80.0")));
        when(quoteService.getIndexQuoteByDate(eq("^GSPC"), any())).thenReturn(Optional.of(createIndexQuote(100.0)), Optional.of(createIndexQuote(120.0)));
        mockEventPublisher();

        ReportItem result = calculator.calculateDownsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isLessThan(0);
    }

    @Test
    @DisplayName("Should return score -3 when stock underperforms by 25-50%")
    void shouldReturnScoreMinusThreeWhenUnderperformanceMedium() {
        when(quoteService.getFirstStockQuote("AAPL")).thenReturn(Optional.of(createStockQuote("2020-01-01", "100.0")));
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(createStockQuote("2024-01-01", "75.0")));
        when(quoteService.getIndexQuoteByDate(eq("^GSPC"), any())).thenReturn(Optional.of(createIndexQuote(100.0)), Optional.of(createIndexQuote(120.0)));
        mockEventPublisher();

        ReportItem result = calculator.calculateDownsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-3);
    }

    @Test
    @DisplayName("Should return score -5 when stock underperforms by > 50%")
    void shouldReturnScoreMinusFiveWhenUnderperformanceHigh() {
        when(quoteService.getFirstStockQuote("AAPL")).thenReturn(Optional.of(createStockQuote("2020-01-01", "100.0")));
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(createStockQuote("2024-01-01", "40.0")));
        when(quoteService.getIndexQuoteByDate(eq("^GSPC"), any())).thenReturn(Optional.of(createIndexQuote(100.0)), Optional.of(createIndexQuote(120.0)));
        mockEventPublisher();

        ReportItem result = calculator.calculateDownsidePerformance("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-5);
    }
}
