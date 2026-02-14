package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.util.SafeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class OrganicGrowthRunawayCalculatorTest {

    private OrganicGrowthRunawayCalculator calculator;

    @Mock private CompanyOverviewRepository companyOverviewRepository;
    @Mock private SecFilingRepository secFilingRepository;
    @Mock private IncomeStatementRepository incomeStatementRepository;
    @Mock private FinancialRatiosRepository financialRatiosRepository;
    @Mock private LlmService llmService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OptionalityCalculator optionalityCalculator;
    @Mock private SafeParser safeParser;
    @Mock private Resource promptResource;

    @BeforeEach
    void setUp() throws java.io.IOException {
        MockitoAnnotations.openMocks(this);
        calculator = new OrganicGrowthRunawayCalculator(
                companyOverviewRepository, secFilingRepository, incomeStatementRepository,
                financialRatiosRepository, llmService, eventPublisher,
                optionalityCalculator, safeParser
        );

        String testPrompt = "Test prompt with {management_discussion} and {latest_earnings_transcript}";
        java.io.InputStream inputStream = new java.io.ByteArrayInputStream(testPrompt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(promptResource.getInputStream()).thenReturn(inputStream);

        ReflectionTestUtils.setField(calculator, "organicGrowthPrompt", promptResource);
    }

    @Test
    void calculate_returnsError_whenLlmFails() {
        String ticker = "AAPL";
        // Logic to reach catch block or return error score
        ReportItem result = calculator.calculate(ticker, new SseEmitter());
        assertEquals(-10, result.getScore());
    }
}
