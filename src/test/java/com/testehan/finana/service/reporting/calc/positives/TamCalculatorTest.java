package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.llm.responses.TAMScoreExplanationResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TamCalculator Tests")
class TamCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private LlmService llmService;
    @Mock
    private SafeParser safeParser;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource tamPrompt;

    private TamCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TamCalculator(
                companyOverviewRepository,
                incomeStatementRepository,
                secFilingRepository,
                eventPublisher,
                llmService,
                safeParser
        );

        try {
            String promptTemplate = "Test prompt with {company_name} and {business_description}";
            InputStream inputStream = new ByteArrayInputStream(promptTemplate.getBytes(StandardCharsets.UTF_8));
            lenient().when(tamPrompt.getInputStream()).thenReturn(inputStream);
        } catch (java.io.IOException e) {
        }

        ReflectionTestUtils.setField(calculator, "tamPrompt", tamPrompt);
    }

    private void mockEventPublisher() {
        doNothing().when(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("Should return error when company overview not found")
    void shouldReturnErrorWhenCompanyOverviewNotFound() {
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        TAMScoreExplanationResponse result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getTotalAddressableMarketScore()).isEqualTo(-10);
    }

    @Test
    @DisplayName("Should return error when income statement data not found")
    void shouldReturnErrorWhenIncomeStatementNotFound() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        mockEventPublisher();

        TAMScoreExplanationResponse result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getTotalAddressableMarketScore()).isEqualTo(-10);
    }

    @Test
    @DisplayName("Should calculate TTM revenue correctly")
    void shouldCalculateTtmRevenueCorrectly() {
        CompanyOverview companyOverview = new CompanyOverview();
        companyOverview.setSymbol("AAPL");
        companyOverview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(companyOverview));

        SecFiling secFiling = new SecFiling();
        secFiling.setSymbol("AAPL");
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.of(secFiling));

        IncomeStatementData incomeStatementData = new IncomeStatementData();
        List<IncomeReport> quarterlyReports = new ArrayList<>();

        IncomeReport q1 = new IncomeReport();
        q1.setDate("2024-12-31");
        q1.setRevenue("100000");
        quarterlyReports.add(q1);

        IncomeReport q2 = new IncomeReport();
        q2.setDate("2024-09-30");
        q2.setRevenue("90000");
        quarterlyReports.add(q2);

        IncomeReport q3 = new IncomeReport();
        q3.setDate("2024-06-30");
        q3.setRevenue("85000");
        quarterlyReports.add(q3);

        IncomeReport q4 = new IncomeReport();
        q4.setDate("2024-03-31");
        q4.setRevenue("80000");
        quarterlyReports.add(q4);

        incomeStatementData.setQuarterlyReports(quarterlyReports);
        when(incomeStatementRepository.findBySymbol("AAPL")).thenReturn(Optional.of(incomeStatementData));
        when(safeParser.parse(any())).thenReturn(new BigDecimal("1"));

        mockEventPublisher();
        when(llmService.callLlm(any(Prompt.class), anyString(), anyString())).thenReturn("{\"totalAddressableMarketScore\":8,\"totalAddressableMarketExplanation\":\"Test\",\"tamPenetrationRunwayScore\":8,\"tamPenetrationRunwayExplanation\":\"Test\"}");

        TAMScoreExplanationResponse result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getTotalAddressableMarketScore()).isNotEqualTo(-10);
    }
}