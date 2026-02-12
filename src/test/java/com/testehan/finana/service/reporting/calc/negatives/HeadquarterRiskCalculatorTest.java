package com.testehan.finana.service.reporting.calc.negatives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeadquarterRiskCalculator Tests")
class HeadquarterRiskCalculatorTest {

    @Mock
    private CompanyOverviewRepository repository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;

    private HeadquarterRiskCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new HeadquarterRiskCalculator(repository, eventPublisher);
    }

    @Test
    @DisplayName("Should return -10 when no data is found")
    void shouldReturnMinusTenWhenNoData() {
        when(repository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getName()).isEqualTo("headquarters");
        assertThat(result.getScore()).isEqualTo(-10);
        verify(eventPublisher).publishEvent(any(ErrorEvent.class));
    }

    @Test
    @DisplayName("Should return 0 when headquarters in safe country")
    void shouldReturnZeroWhenSafeCountry() {
        CompanyOverview overview = new CompanyOverview();
        overview.setCountry("USA");
        when(repository.findBySymbol("AAPL")).thenReturn(Optional.of(overview));

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("USA");
    }

    @Test
    @DisplayName("Should return -3 when headquarters in risky country")
    void shouldReturnMinusThreeWhenRiskyCountry() {
        CompanyOverview overview = new CompanyOverview();
        overview.setCountry("China");
        when(repository.findBySymbol("BABA")).thenReturn(Optional.of(overview));

        ReportItem result = calculator.calculate("BABA", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-3);
        assertThat(result.getExplanation()).contains("China");
    }
}
