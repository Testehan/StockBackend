package com.testehan.finana.service.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testehan.finana.model.qa.QuestionAnswer;
import com.testehan.finana.model.qa.QuestionAnswerStatus;
import com.testehan.finana.model.qa.StockSentiment;
import com.testehan.finana.repository.QuestionAnswerRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.TranscriptAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionAnswerServiceImplTest {

    private QuestionAnswerServiceImpl questionAnswerService;

    @Mock private QuestionAnswerRepository questionAnswerRepository;
    @Mock private LlmService llmService;
    @Mock private LlmQuestionAnswerGenerator llmQuestionAnswerGenerator;
    @Mock private TranscriptAnalysisService transcriptAnalysisService;
    private ObjectMapper objectMapper = new ObjectMapper();
    private String llmModel = "gemini-1.5-flash";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        questionAnswerService = new QuestionAnswerServiceImpl(
                questionAnswerRepository, llmService, llmQuestionAnswerGenerator,
                transcriptAnalysisService, objectMapper, llmModel
        );
    }

    @Test
    void answerQuestion_transcriptQuestion_callsTranscriptAnalysisService() {
        String stockId = "AAPL";
        String questionId = "transcript_30_seconds_summary";
        SseEmitter emitter = new SseEmitter();

        questionAnswerService.answerQuestion(stockId, questionId, "Q1-2023", emitter, false);

        verify(transcriptAnalysisService).analyzeTranscript(eq(stockId), eq(questionId), eq("Q1-2023"), any());
    }

    @Test
    void answerQuestion_businessQuestion_newRecord_callsGenerator() {
        String stockId = "AAPL";
        String questionId = "moat";
        SseEmitter emitter = new SseEmitter();

        when(questionAnswerRepository.findByStockIdAndQuestionIdAndPromptVersionAndModel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        questionAnswerService.answerQuestion(stockId, questionId, null, emitter, false);

        verify(questionAnswerRepository).save(any(QuestionAnswer.class));
        verify(llmQuestionAnswerGenerator).generateAnswerStreaming(eq(stockId), eq(questionId), anyString(), eq(llmModel), any());
    }

    @Test
    void answerQuestion_businessQuestion_existingCompleted_sendsExisting() throws Exception {
        String stockId = "AAPL";
        String questionId = "moat";
        SseEmitter emitter = mock(SseEmitter.class);
        QuestionAnswer existing = new QuestionAnswer();
        existing.setStatus(QuestionAnswerStatus.COMPLETED);
        existing.setAnswer("Existing Answer");

        when(questionAnswerRepository.findByStockIdAndQuestionIdAndPromptVersionAndModel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existing));

        questionAnswerService.answerQuestion(stockId, questionId, null, emitter, false);

        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(llmQuestionAnswerGenerator, never()).generateAnswerStreaming(any(), any(), any(), any(), any());
    }

    @Test
    void getSentiment_existingCompleted_returnsParsedSentiment() throws Exception {
        String stockId = "AAPL";
        QuestionAnswer existing = new QuestionAnswer();
        existing.setStatus(QuestionAnswerStatus.COMPLETED);
        StockSentiment sentiment = new StockSentiment();
        sentiment.setTicker("AAPL");
        sentiment.setLabel("Bullish");
        existing.setAnswer(objectMapper.writeValueAsString(sentiment));
        existing.setUpdatedAt(LocalDateTime.now());

        when(questionAnswerRepository.findByStockIdAndQuestionIdAndPromptVersionAndModel(eq("AAPL"), eq("sentiment"), anyString(), eq(llmModel)))
                .thenReturn(Optional.of(existing));

        StockSentiment result = questionAnswerService.getSentiment(stockId, false);

        assertEquals("Bullish", result.getLabel());
        assertEquals("AAPL", result.getTicker());
    }
}
