package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.repository.SecFilingRepository;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class OptionalityCalculatorTest {

    private OptionalityCalculator calculator;

    @Mock private CompanyOverviewRepository companyOverviewRepository;
    @Mock private IncomeStatementRepository incomeStatementRepository;
    @Mock private SecFilingRepository secFilingRepository;
    @Mock private FinancialRatiosRepository financialRatiosRepository;
    @Mock private LlmService llmService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SafeParser safeParser;
    @Mock private DateUtils dateUtils;
    @Mock private FinancialDataOrchestrator financialDataOrchestrator;
    @Mock private EarningsService earningsService;
    @Mock private Resource promptResource;

    @BeforeEach
    void setUp() throws java.io.IOException {
        MockitoAnnotations.openMocks(this);
        calculator = new OptionalityCalculator(
                companyOverviewRepository, incomeStatementRepository, secFilingRepository,
                financialRatiosRepository, llmService, eventPublisher, safeParser,
                dateUtils, financialDataOrchestrator, earningsService
        );

        String testPrompt = "Test prompt with {business_description} and {latest_earnings_transcript}";
        java.io.InputStream inputStream = new java.io.ByteArrayInputStream(testPrompt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(promptResource.getInputStream()).thenReturn(inputStream);

        ReflectionTestUtils.setField(calculator, "optionalityPrompt", promptResource);
    }

    @Test
    void calculate_returnsError_when10kMissing() {
        String ticker = "AAPL";
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.of(new CompanyOverview()));
        when(secFilingRepository.findBySymbol(ticker)).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate(ticker, new SseEmitter());

        assertEquals(-10, result.getScore());
    }
}
