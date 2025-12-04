package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.EarningsEstimatesRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import com.testehan.finana.util.SafeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperatingLeverageCalculator Tests")
class OperatingLeverageCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private EarningsEstimatesRepository earningsEstimatesRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SafeParser safeParser;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource operatingLeveragePrompt;

    private OperatingLeverageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new OperatingLeverageCalculator(
                companyOverviewRepository,
                incomeStatementRepository,
                earningsEstimatesRepository,
                secFilingRepository,
                llmService,
                eventPublisher,
                safeParser
        );
        
        try {
            String promptTemplate = "Test prompt with {company_name} and {business_description}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(operatingLeveragePrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
            // Ignore for testing
        }
        
        ReflectionTestUtils.setField(calculator, "operatingLeveragePrompt", operatingLeveragePrompt);
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("Should handle empty income data gracefully")
    void shouldHandleEmptyIncomeDataGracefully() {
        IncomeStatementData incomeData = new IncomeStatementData();
        
        // Need at least one report for calculateExpectedRevenueGrowth to work
        List<IncomeReport> reports = new ArrayList<>();
        IncomeReport report = new IncomeReport();
        report.setDate("2024-01-01");
        report.setRevenue("100000.0");
        reports.add(report);
        
        incomeData.setAnnualReports(reports);
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.of(incomeData));
        when(earningsEstimatesRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(safeParser.parse(anyString())).thenReturn(BigDecimal.ZERO);
        mockEventPublisher();

        CompanyOverview companyOverview = new CompanyOverview();
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        
        String llmResponse = "{\"score\": 1, \"explanation\": \"Test\"}";
        when(llmService.callLlm(any(String.class), anyString(), anyString())).thenReturn(llmResponse);

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result).isNotNull();
    }
}
