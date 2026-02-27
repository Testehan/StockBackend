package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.EarningsService;
import com.testehan.finana.service.FinancialDataOrchestrator;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.util.DateUtils;
import com.testehan.finana.util.SafeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class ReinvestmentRunwayCalculatorTest {

    private ReinvestmentRunwayCalculator calculator;

    @Mock private CompanyOverviewRepository companyOverviewRepository;
    @Mock private IncomeStatementRepository incomeStatementRepository;
    @Mock private SecFilingRepository secFilingRepository;
    @Mock private RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    @Mock private RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository;
    @Mock private FinancialDataOrchestrator financialDataOrchestrator;
    @Mock private EarningsService earningsService;
    @Mock private LlmService llmService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SafeParser safeParser;
    @Mock private DateUtils dateUtils;
    @Mock private CashFlowRepository cashFlowRepository;
    @Mock private Resource promptResource;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new ReinvestmentRunwayCalculator(
                companyOverviewRepository, incomeStatementRepository, secFilingRepository,
                revenueSegmentationDataRepository, revenueGeographicSegmentationRepository,
                financialDataOrchestrator, earningsService, llmService, eventPublisher,
                safeParser, dateUtils, cashFlowRepository
        );
        ReflectionTestUtils.setField(calculator, "reinvestmentRunwayPrompt", promptResource);
    }

    @Test
    void calculate_returnsErrorReportItem_whenOverviewMissing() {
        String ticker = "AAPL";
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate(ticker, new SseEmitter());

        assertEquals(0, result.getScore());
    }
}
