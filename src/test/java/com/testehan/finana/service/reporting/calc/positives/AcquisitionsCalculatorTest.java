package com.testehan.finana.service.reporting.calc.positives;

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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcquisitionsCalculator Tests")
class AcquisitionsCalculatorTest {

    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;

    private AcquisitionsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AcquisitionsCalculator(incomeStatementRepository, eventPublisher);
    }

    @Test
    @DisplayName("Should calculate acquisition score successfully")
    void shouldCalculateAcquisitionsSuccessfully() {
        String ticker = "MSFT";
        IncomeStatementData incomeData = new IncomeStatementData();
        
        IncomeReport r1 = new IncomeReport();
        r1.setDate("2023-06-30");
        r1.setSellingAndMarketingExpenses("100");
        r1.setGrossProfit("1000"); // 10%
        
        IncomeReport r2 = new IncomeReport();
        r2.setDate("2022-06-30");
        r2.setSellingAndMarketingExpenses("200");
        r2.setGrossProfit("1000"); // 20%
        
        IncomeReport r3 = new IncomeReport();
        r3.setDate("2021-06-30");
        r3.setSellingAndMarketingExpenses("300");
        r3.setGrossProfit("1000"); // 30%
        
        incomeData.setAnnualReports(List.of(r1, r2, r3));
        when(incomeStatementRepository.findBySymbol(ticker)).thenReturn(Optional.of(incomeData));

        // Weighted Average Calculation:
        // ((10 * 3) + (20 * 2) + (30 * 1)) / (3 + 2 + 1)
        // (30 + 40 + 30) / 6 = 100 / 6 = 16.67
        // Score for 16.67 is 4 (val < 20)

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("customerAcquisition");
        assertThat(result.getScore()).isEqualTo(4);
        assertThat(result.getExplanation()).contains("16.67%");
    }

    @Test
    @DisplayName("Should use SG&A as fallback if S&M is missing")
    void shouldFallbackToSga() {
        String ticker = "MSFT";
        IncomeStatementData incomeData = new IncomeStatementData();
        
        IncomeReport r1 = new IncomeReport();
        r1.setDate("2023-06-30");
        r1.setSellingAndMarketingExpenses("0");
        r1.setSellingGeneralAndAdministrativeExpenses("150");
        r1.setGrossProfit("1000"); // 15%
        
        incomeData.setAnnualReports(List.of(r1));
        when(incomeStatementRepository.findBySymbol(ticker)).thenReturn(Optional.of(incomeData));

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result.getScore()).isEqualTo(4); // 15% < 20%
        assertThat(result.getExplanation()).contains("15.00%");
    }

    @Test
    @DisplayName("Should handle missing income data")
    void shouldHandleMissingData() {
        String ticker = "UNKNOWN";
        when(incomeStatementRepository.findBySymbol(ticker)).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
        verify(eventPublisher).publishEvent(any(ErrorEvent.class));
    }
}
