package com.testehan.finana.service.qa;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.qa.QuestionAnswer;
import com.testehan.finana.model.qa.QuestionAnswerStatus;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.QuestionAnswerRepository;
import com.testehan.finana.service.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LlmQuestionAnswerGeneratorTest {

    private LlmQuestionAnswerGenerator generator;

    @Mock private LlmService llmService;
    @Mock private CompanyOverviewRepository companyOverviewRepository;
    @Mock private QuestionAnswerRepository questionAnswerRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        generator = new LlmQuestionAnswerGenerator(llmService, companyOverviewRepository, questionAnswerRepository);
        
        ReflectionTestUtils.setField(generator, "questionPrompt", new ByteArrayResource("{{question}}".getBytes()));
        ReflectionTestUtils.setField(generator, "guruPrompt", new ByteArrayResource("{{question}}".getBytes()));
    }

    @Test
    void generateAnswerStreaming_success_updatesRepository() {
        String stockId = "AAPL";
        String questionId = "revenue_model"; // Using valid question ID
        SseEmitter emitter = mock(SseEmitter.class);

        CompanyOverview overview = new CompanyOverview();
        overview.setCompanyName("Apple Inc");
        overview.setWebsite("https://apple.com");
        when(companyOverviewRepository.findBySymbol(stockId)).thenReturn(Optional.of(overview));

        when(llmService.streamLlmWithSearch(any(Prompt.class), eq(questionId), eq(stockId)))
                .thenReturn(Flux.just("Chunk 1", "Chunk 2"));

        QuestionAnswer qa = new QuestionAnswer();
        qa.setStockId(stockId);
        qa.setQuestionId(questionId);
        when(questionAnswerRepository.findByStockIdAndQuestionIdAndPromptVersionAndModel(any(), any(), any(), any()))
                .thenReturn(Optional.of(qa));

        generator.generateAnswerStreaming(stockId, questionId, "v1", "model", emitter);

        verify(llmService).streamLlmWithSearch(any(), eq(questionId), eq(stockId));
        verify(questionAnswerRepository, timeout(1000)).save(argThat(savedQa -> 
            savedQa.getStatus() == QuestionAnswerStatus.COMPLETED &&
            savedQa.getAnswer().contains("Chunk 1Chunk 2")
        ));
    }
}
