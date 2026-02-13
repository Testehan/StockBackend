package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.filing.TenKFilings;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.SecFilingRepository;
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
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionStatementCalculator Tests")
class MissionStatementCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private SecFilingRepository secFilingRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource missionStatementPrompt;

    private MissionStatementCalculator calculator;

    @BeforeEach
    void setUp() throws IOException {
        calculator = new MissionStatementCalculator(
                companyOverviewRepository,
                secFilingRepository,
                llmService,
                eventPublisher
        );

        String promptContent = "Analyze mission statement for {company_description}. Business: {business_description}. Format: {format}";
        lenient().when(missionStatementPrompt.getInputStream()).thenReturn(new ByteArrayInputStream(promptContent.getBytes()));
        ReflectionTestUtils.setField(calculator, "missionStatementPrompt", missionStatementPrompt);
    }

    @Test
    @DisplayName("Should calculate mission statement score successfully")
    void shouldCalculateMissionStatementSuccessfully() {
        String ticker = "TSLA";
        CompanyOverview overview = new CompanyOverview();
        overview.setDescription("Accelerating the world's transition to sustainable energy.");
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.of(overview));

        SecFiling secFiling = new SecFiling();
        TenKFilings tenK = new TenKFilings();
        tenK.setBusinessDescription("Electric vehicles and solar panels.");
        tenK.setFiledAt("2023-01-30");
        secFiling.setTenKFilings(List.of(tenK));
        when(secFilingRepository.findBySymbol(ticker)).thenReturn(Optional.of(secFiling));

        String llmResponse = "{\"score\": 5, \"explanation\": \"Strong, clear mission statement.\"}";
        when(llmService.callLlmWithSearch(anyString(), eq("mission_statement_analysis"), eq(ticker))).thenReturn(llmResponse);

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("missionStatement");
        assertThat(result.getScore()).isEqualTo(5);
        assertThat(result.getExplanation()).isEqualTo("Strong, clear mission statement.");
        verify(eventPublisher, atLeastOnce()).publishEvent(any(MessageEvent.class));
    }

    @Test
    @DisplayName("Should handle missing company overview")
    void shouldHandleMissingCompanyOverview() {
        String ticker = "UNKNOWN";
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
        verify(eventPublisher).publishEvent(any(ErrorEvent.class));
    }

    @Test
    @DisplayName("Should handle LLM failure")
    void shouldHandleLlmFailure() {
        String ticker = "TSLA";
        CompanyOverview overview = new CompanyOverview();
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.of(overview));

        when(llmService.callLlmWithSearch(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
        assertThat(result.getExplanation()).contains("failed");
        verify(eventPublisher, atLeastOnce()).publishEvent(any(ErrorEvent.class));
    }
}
