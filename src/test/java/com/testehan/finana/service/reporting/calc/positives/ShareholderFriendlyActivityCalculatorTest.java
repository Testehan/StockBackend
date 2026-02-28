package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.finstatement.CashFlowData;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CashFlowRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ShareholderFriendlyActivityCalculatorTest {

    private ShareholderFriendlyActivityCalculator calculator;

    @Mock private CompanyOverviewRepository companyOverviewRepository;
    @Mock private CashFlowRepository cashFlowRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new ShareholderFriendlyActivityCalculator(
                companyOverviewRepository, cashFlowRepository, eventPublisher
        );
    }

    @Test
    void calculate_returnsZero_whenDataMissing() {
        String ticker = "AAPL";
        when(cashFlowRepository.findBySymbol(ticker)).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate(ticker, new SseEmitter());

        assertEquals(0, result.getScore());
    }
}
