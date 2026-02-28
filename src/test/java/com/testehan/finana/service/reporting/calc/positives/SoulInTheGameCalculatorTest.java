package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SoulInTheGameCalculator Tests")
class SoulInTheGameCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SseEmitter sseEmitter;
    @Mock
    private Resource soulInTheGamePrompt;

    private SoulInTheGameCalculator calculator;

    @BeforeEach
    void setUp() throws IOException {
        calculator = new SoulInTheGameCalculator(
                companyOverviewRepository,
                llmService,
                eventPublisher
        );

        String promptContent = "Analyze soul in the game for {company_description} and {company_ceo}. Format: {format}";
        lenient().when(soulInTheGamePrompt.getInputStream()).thenReturn(new ByteArrayInputStream(promptContent.getBytes()));
        ReflectionTestUtils.setField(calculator, "soulInTheGamePrompt", soulInTheGamePrompt);
    }

    @Test
    @DisplayName("Should calculate soul in the game score successfully")
    void shouldCalculateSoulInTheGameSuccessfully() {
        String ticker = "AMZN";
        CompanyOverview overview = new CompanyOverview();
        overview.setDescription("E-commerce and cloud computing.");
        overview.setCeo("Andy Jassy");
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.of(overview));

        String llmResponse = "{\"score\": 4, \"explanation\": \"CEO has significant stake and long history.\"}";
        when(llmService.callLlmWithSearch(anyString(), eq("soul_in_the_game_analysis"), eq(ticker))).thenReturn(llmResponse);

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("soulInTheGame");
        assertThat(result.getScore()).isEqualTo(4);
        assertThat(result.getExplanation()).isEqualTo("CEO has significant stake and long history.");
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
        String ticker = "AMZN";
        CompanyOverview overview = new CompanyOverview();
        when(companyOverviewRepository.findBySymbol(ticker)).thenReturn(Optional.of(overview));

        when(llmService.callLlmWithSearch(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("LLM failure"));

        ReportItem result = calculator.calculate(ticker, sseEmitter);

        assertThat(result.getScore()).isEqualTo(-10);
        assertThat(result.getExplanation()).contains("failed");
        verify(eventPublisher).publishEvent(any(ErrorEvent.class));
    }
}
