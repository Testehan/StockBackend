package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
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
@DisplayName("GrowthCurveCalculator Tests")
class GrowthCurveCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private LlmService llmService;
    @Mock
    private FinancialRatiosRepository financialRatiosRepository;
    @Mock
    private CashFlowRepository cashFlowRepository;
    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private BalanceSheetRepository balanceSheetRepository;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource growthCurvePrompt;

    private GrowthCurveCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new GrowthCurveCalculator(
                companyOverviewRepository,
                secFilingRepository,
                eventPublisher,
                llmService,
                financialRatiosRepository,
                cashFlowRepository,
                incomeStatementRepository,
                balanceSheetRepository
        );
        
        try {
            String promptTemplate = "Test prompt with {company_name} and {mda}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(growthCurvePrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
            // Ignore for testing
        }
        
        ReflectionTestUtils.setField(calculator, "growthCurvePrompt", growthCurvePrompt);
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("Should return error when company overview not found")
    void shouldReturnErrorWhenCompanyOverviewNotFound() {
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        ReportItem result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
    }
}
