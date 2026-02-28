package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ScalabilityOfModelCalculatorTest {

    private ScalabilityOfModelCalculator calculator;

    @Mock private CompanyOverviewRepository companyOverviewRepository;
    @Mock private SecFilingRepository secFilingRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private LlmService llmService;
    @Mock private FinancialRatiosRepository financialRatiosRepository;
    @Mock private CashFlowRepository cashFlowRepository;
    @Mock private IncomeStatementRepository incomeStatementRepository;
    @Mock private BalanceSheetRepository balanceSheetRepository;
    @Mock private Resource promptResource;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new ScalabilityOfModelCalculator(
                companyOverviewRepository, secFilingRepository, eventPublisher,
                llmService, financialRatiosRepository, cashFlowRepository,
                incomeStatementRepository, balanceSheetRepository
        );
        ReflectionTestUtils.setField(calculator, "scalabilityOfModelPrompt", promptResource);
    }

    @Test
    void calculate_returnsError_whenOverviewMissing() {
        String ticker = "AAPL";
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate(ticker, new SseEmitter());

        assertEquals(-10, result.getScore());
    }
}
