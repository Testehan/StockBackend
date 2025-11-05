package com.testehan.finana.service.qa;

import com.testehan.finana.model.qa.BusinessAnalysisQuestions;
import com.testehan.finana.model.qa.QuestionAnswer;
import com.testehan.finana.model.qa.QuestionAnswerStatus;
import com.testehan.finana.repository.QuestionAnswerRepository;
import com.testehan.finana.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class QuestionAnswerServiceImpl implements QuestionAnswerService {

    private static final Logger logger = LoggerFactory.getLogger(QuestionAnswerServiceImpl.class);
    private static final String PROMPT_VERSION = "v1";

    private final QuestionAnswerRepository questionAnswerRepository;
    private final LlmService llmService; // Keep LlmService for synchronous operations if any, or remove if not needed elsewhere
    private final LlmQuestionAnswerGenerator llmQuestionAnswerGenerator;
    private final String llmModel;

    public QuestionAnswerServiceImpl(QuestionAnswerRepository questionAnswerRepository,
                                     LlmService llmService,
                                     LlmQuestionAnswerGenerator llmQuestionAnswerGenerator,
                                     @Value("${spring.ai.google.genai.chat.options.model}") String llmModel) {
        this.questionAnswerRepository = questionAnswerRepository;
        this.llmService = llmService;
        this.llmQuestionAnswerGenerator = llmQuestionAnswerGenerator;
        this.llmModel = llmModel;
    }

        @Override
        public void answerQuestion(String stockId, String questionId, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
            Optional<QuestionAnswer> existingAnswerOpt = questionAnswerRepository
                    .findByStockIdAndQuestionIdAndPromptVersionAndModel(stockId, questionId, PROMPT_VERSION, llmModel);
    
            if (existingAnswerOpt.isPresent()) {
                QuestionAnswer questionAnswer = existingAnswerOpt.get();
                if (questionAnswer.getStatus() == QuestionAnswerStatus.COMPLETED) {
                    logger.info("Answer already exists and is completed for stockId: {}, questionId: {}", stockId, questionId);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(questionAnswer.getAnswer()));
                        emitter.send(SseEmitter.event().name("COMPLETED").data("")); // Send COMPLETED event
                        emitter.complete();
                    } catch (java.io.IOException e) {
                        emitter.completeWithError(e);
                    }
                    return;
                } else if (questionAnswer.getStatus() == QuestionAnswerStatus.IN_PROGRESS) {
                    logger.info("Answer generation is already in progress for stockId: {}, questionId: {}", stockId, questionId);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("message").data("Answer generation is already in progress."));
                        emitter.send(SseEmitter.event().name("COMPLETED").data("")); // Send COMPLETED event
                        emitter.complete();
                    } catch (java.io.IOException e) {
                        emitter.completeWithError(e);
                    }
                    return;
                } else { // FAILED or other states
                    logger.info("Retrying answer generation for stockId: {}, questionId: {}", stockId, questionId);
                    questionAnswer.setStatus(QuestionAnswerStatus.IN_PROGRESS);
                    questionAnswer.setUpdatedAt(LocalDateTime.now());
                    questionAnswerRepository.save(questionAnswer);
                }
            } else {
                logger.info("Creating new QuestionAnswer record for stockId: {}, questionId: {}", stockId, questionId);
                QuestionAnswer questionAnswer = new QuestionAnswer();
                questionAnswer.setStockId(stockId);
                questionAnswer.setQuestionId(questionId);
                questionAnswer.setStatus(QuestionAnswerStatus.IN_PROGRESS);
                questionAnswer.setModel(llmModel);
                questionAnswer.setPromptVersion(PROMPT_VERSION);
                questionAnswer.setCreatedAt(LocalDateTime.now());
                questionAnswer.setUpdatedAt(LocalDateTime.now());
                questionAnswerRepository.save(questionAnswer);
            }
    
            String questionText = BusinessAnalysisQuestions.QUESTIONS.stream()
                    .filter(q -> q.getId().equals(questionId))
                    .map(q -> q.getText())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Question not found with ID: " + questionId));
    
            llmQuestionAnswerGenerator.generateAnswerStreaming(stockId, questionId, PROMPT_VERSION, llmModel, questionText, emitter);
        }}
