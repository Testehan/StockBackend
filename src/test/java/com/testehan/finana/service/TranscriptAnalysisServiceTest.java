package com.testehan.finana.service;

import com.testehan.finana.model.filing.CompanyEarningsTranscripts;
import com.testehan.finana.model.filing.QuarterlyEarningsTranscript;
import com.testehan.finana.repository.CompanyEarningsTranscriptsRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TranscriptAnalysisServiceTest {

    private TranscriptAnalysisService transcriptAnalysisService;

    @Mock private CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository;
    @Mock private CompanyOverviewRepository companyOverviewRepository;
    @Mock private LlmService llmService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transcriptAnalysisService = new TranscriptAnalysisService(
                companyEarningsTranscriptsRepository, companyOverviewRepository, llmService
        );

        // Inject mock resources to avoid null pointers when creating PromptTemplate
        ReflectionTestUtils.setField(transcriptAnalysisService, "transcript30SecondsSummaryPrompt", new ByteArrayResource("{{transcript}}".getBytes()));
    }

    @Test
    void analyzeTranscript_existingAnswer_sendsAnswerDirectly() throws Exception {
        String stockId = "AAPL";
        String questionId = "transcript_30_seconds_summary";
        SseEmitter emitter = mock(SseEmitter.class);

        CompanyEarningsTranscripts companyTranscripts = new CompanyEarningsTranscripts();
        QuarterlyEarningsTranscript transcript = new QuarterlyEarningsTranscript();
        transcript.setQuarter("Q1-2023");
        Map<String, String> existingAnswers = new HashMap<>();
        existingAnswers.put(questionId, "Existing Summary");
        transcript.setTranscriptAnalysisAnswers(existingAnswers);
        companyTranscripts.setTranscripts(new ArrayList<>(List.of(transcript)));

        when(companyEarningsTranscriptsRepository.findById(stockId.toUpperCase())).thenReturn(Optional.of(companyTranscripts));

        transcriptAnalysisService.analyzeTranscript(stockId, questionId, "Q1-2023", emitter);

        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(llmService, never()).streamLlmWithSearch(any(), any(), any());
    }

    @Test
    void analyzeTranscript_noExistingAnswer_callsLlm() {
        String stockId = "AAPL";
        String questionId = "transcript_30_seconds_summary";
        SseEmitter emitter = mock(SseEmitter.class);

        CompanyEarningsTranscripts companyTranscripts = new CompanyEarningsTranscripts();
        QuarterlyEarningsTranscript transcript = new QuarterlyEarningsTranscript();
        transcript.setQuarter("Q1-2023");
        transcript.setTranscript(new ArrayList<>());
        companyTranscripts.setTranscripts(new ArrayList<>(List.of(transcript)));

        when(companyEarningsTranscriptsRepository.findById(stockId.toUpperCase())).thenReturn(Optional.of(companyTranscripts));
        when(companyOverviewRepository.findBySymbol(anyString())).thenReturn(Optional.empty());
        when(llmService.streamLlmWithSearch(any(), any(), any())).thenReturn(Flux.just("Chunk"));

        transcriptAnalysisService.analyzeTranscript(stockId, questionId, "Q1-2023", emitter);

        verify(llmService).streamLlmWithSearch(any(), eq(questionId), eq(stockId));
    }
}
