package com.testehan.finana.service.reporting.calc.negatives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.filing.TenKFilings;
import com.testehan.finana.model.llm.responses.FerolNegativesAnalysisLlmResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.service.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultipleRisksCalculator Tests")
class MultipleRisksCalculatorTest {

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

    private MultipleRisksCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MultipleRisksCalculator(companyOverviewRepository, secFilingRepository, llmService, eventPublisher);
        ReflectionTestUtils.setField(calculator, "multipleNegativesPrompt", new ByteArrayResource("test prompt {format} {company_name} {business_description} {risk_factors} {management_discussion}".getBytes()));
    }

    @Test
    @DisplayName("Should return failure response when data is missing")
    void shouldReturnFailureWhenDataMissing() {
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        FerolNegativesAnalysisLlmResponse result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getAccountingIrregularitiesScore()).isEqualTo(-10);
    }

    @Test
    @DisplayName("Should return LLM response when data is present")
    void shouldReturnLlmResponse() {
        CompanyOverview overview = new CompanyOverview();
        overview.setCompanyName("Apple Inc.");
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(overview));

        SecFiling filing = new SecFiling();
        TenKFilings tenK = new TenKFilings();
        tenK.setBusinessDescription("Description");
        tenK.setRiskFactors("Risks");
        tenK.setManagementDiscussion("Discussion");
        tenK.setFiledAt("2023-01-01");
        filing.setTenKFilings(List.of(tenK));
        when(secFilingRepository.findBySymbol("AAPL")).thenReturn(Optional.of(filing));

        String mockLlmResponse = "{\"accountingIrregularitiesScore\": -1, \"accountingIrregularitiesExplanation\": \"Explanation\"}";
        when(llmService.callLlmWithOllama(any(Prompt.class), eq("multiple_risks_analysis"), eq("AAPL")))
                .thenReturn(mockLlmResponse);

        FerolNegativesAnalysisLlmResponse result = calculator.calculate("AAPL", sseEmitter);

        assertThat(result.getAccountingIrregularitiesScore()).isEqualTo(-1);
        assertThat(result.getAccountingIrregularitiesExplanation()).isEqualTo("Explanation");
    }
}
