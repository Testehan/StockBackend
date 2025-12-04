package com.testehan.finana.service.qa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testehan.finana.model.qa.QuestionAnswer;
import com.testehan.finana.model.qa.QuestionAnswerStatus;
import com.testehan.finana.model.qa.QuestionConstants;
import com.testehan.finana.model.qa.StockSentiment;
import com.testehan.finana.repository.QuestionAnswerRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.TranscriptAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class QuestionAnswerServiceImpl implements QuestionAnswerService {

    private static final Logger logger = LoggerFactory.getLogger(QuestionAnswerServiceImpl.class);
    private static final String PROMPT_VERSION = "v1";

    private final QuestionAnswerRepository questionAnswerRepository;
    private final LlmService llmService; 
    private final LlmQuestionAnswerGenerator llmQuestionAnswerGenerator;
    private final TranscriptAnalysisService transcriptAnalysisService;
    private final ObjectMapper objectMapper;
    private final String llmModel;

    @Value("classpath:/prompts/qa/sentiment_prompt.txt")
    private Resource sentimentPrompt;

    public QuestionAnswerServiceImpl(QuestionAnswerRepository questionAnswerRepository,
                                     LlmService llmService,
                                     LlmQuestionAnswerGenerator llmQuestionAnswerGenerator,
                                     TranscriptAnalysisService transcriptAnalysisService,
                                     ObjectMapper objectMapper,
                                     @Value("${spring.ai.google.genai.chat.options.model}") String llmModel) {
        this.questionAnswerRepository = questionAnswerRepository;
        this.llmService = llmService;
        this.llmQuestionAnswerGenerator = llmQuestionAnswerGenerator;
        this.transcriptAnalysisService = transcriptAnalysisService;
        this.objectMapper = objectMapper;
        this.llmModel = llmModel;
    }

    @Override
    public void answerQuestion(String stockId, String questionId, String additionalInformation, SseEmitter emitter, boolean regenerate) {
        boolean isTranscriptQuestion = QuestionConstants.EARNINGS_TRANSCRIPT_QUESTIONS.stream()
                .anyMatch(q -> q.getId().equals(questionId));

        if (isTranscriptQuestion) {
            answerTranscriptQuestion(stockId, questionId, additionalInformation, emitter);
        } else {
            answerBusinessAnalysisQuestion(stockId, questionId, emitter, regenerate);
        }
    }

    private void answerBusinessAnalysisQuestion(String stockId, String questionId, SseEmitter emitter, boolean regenerate) {
        Optional<QuestionAnswer> existingAnswerOpt = questionAnswerRepository
                .findByStockIdAndQuestionIdAndPromptVersionAndModel(stockId, questionId, PROMPT_VERSION, llmModel);

        if (existingAnswerOpt.isPresent()) {
            QuestionAnswer questionAnswer = existingAnswerOpt.get();
            if (!regenerate) {
                if (questionAnswer.getStatus() == QuestionAnswerStatus.COMPLETED) {
                    logger.info("Answer already exists and is completed for stockId: {}, questionId: {}", stockId, questionId);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(questionAnswer.getAnswer()));
                        emitter.send(SseEmitter.event().name("COMPLETED").data("")); 
                        emitter.complete();
                    } catch (java.io.IOException e) {
                        emitter.completeWithError(e);
                    }
                    return;
                } else if (questionAnswer.getStatus() == QuestionAnswerStatus.IN_PROGRESS) {
                    logger.info("Answer generation is already in progress for stockId: {}, questionId: {}", stockId, questionId);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("message").data("Answer generation is already in progress."));
                        emitter.send(SseEmitter.event().name("COMPLETED").data("")); 
                        emitter.complete();
                    } catch (java.io.IOException e) {
                        emitter.completeWithError(e);
                    }
                    return;
                }
            }

            logger.info("{} answer generation for stockId: {}, questionId: {}", regenerate ? "Regenerating" : "Retrying", stockId, questionId);
            questionAnswer.setStatus(QuestionAnswerStatus.IN_PROGRESS);
            questionAnswer.setUpdatedAt(LocalDateTime.now());
            questionAnswerRepository.save(questionAnswer);
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

        String questionText = QuestionConstants.BUSINESS_ANALYSIS_QUESTIONS.stream()
                .filter(q -> q.getId().equals(questionId))
                .map(q -> q.getText())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question not found with ID: " + questionId));

        llmQuestionAnswerGenerator.generateAnswerStreaming(stockId, questionId, PROMPT_VERSION, llmModel, questionText, emitter);
    }

    private void answerTranscriptQuestion(String stockId, String questionId, String additionalInformation, SseEmitter emitter) {
        transcriptAnalysisService.analyzeTranscript(stockId, questionId, additionalInformation, emitter);
    }

    @Override
    public StockSentiment getSentiment(String stockId, boolean regenerate) {
        String ticker = stockId.toUpperCase();
        String questionId = "sentiment";

        Optional<QuestionAnswer> existingAnswerOpt = questionAnswerRepository
                .findByStockIdAndQuestionIdAndPromptVersionAndModel(ticker, questionId, PROMPT_VERSION, llmModel);

        QuestionAnswer questionAnswer;
        if (existingAnswerOpt.isPresent()) {
            questionAnswer = existingAnswerOpt.get();
            if (!regenerate) {
                if (questionAnswer.getStatus() == QuestionAnswerStatus.COMPLETED) {
                    logger.info("Sentiment already exists and is completed for stockId: {}", ticker);
                    try {
                        StockSentiment sentiment = objectMapper.readValue(questionAnswer.getAnswer(), StockSentiment.class);
                        sentiment.setDate(questionAnswer.getUpdatedAt());
                        return sentiment;
                    } catch (JsonProcessingException e) {
                        logger.error("Error parsing existing sentiment JSON", e);
                    }
                } else if (questionAnswer.getStatus() == QuestionAnswerStatus.IN_PROGRESS) {
                    logger.info("Sentiment generation is already in progress for stockId: {}", ticker);
                    StockSentiment sentiment = new StockSentiment();
                    sentiment.setTicker(ticker);
                    sentiment.setLabel("IN_PROGRESS");
                    sentiment.setSummary("Sentiment generation is already in progress...");
                    return sentiment;
                }
            }
            logger.info("{} sentiment generation for stockId: {}", regenerate ? "Regenerating" : "Retrying", ticker);
            questionAnswer.setStatus(QuestionAnswerStatus.IN_PROGRESS);
            questionAnswer.setUpdatedAt(LocalDateTime.now());
            questionAnswerRepository.save(questionAnswer);
        } else {
            logger.info("Creating new QuestionAnswer record for sentiment of stockId: {}", ticker);
            questionAnswer = new QuestionAnswer();
            questionAnswer.setStockId(ticker);
            questionAnswer.setQuestionId(questionId);
            questionAnswer.setStatus(QuestionAnswerStatus.IN_PROGRESS);
            questionAnswer.setModel(llmModel);
            questionAnswer.setPromptVersion(PROMPT_VERSION);
            questionAnswer.setCreatedAt(LocalDateTime.now());
            questionAnswer.setUpdatedAt(LocalDateTime.now());
            try {
                questionAnswer = questionAnswerRepository.save(questionAnswer);
            } catch (DuplicateKeyException e) {
                logger.warn("Duplicate key while inserting sentiment record for {}, retrying getSentiment", ticker);
                return getSentiment(stockId, regenerate);
            }
        }

        try {
            logger.info("Generating sentiment for stockId: {}", ticker);
            var stockSentimentOutputConverter = new BeanOutputConverter<>(StockSentiment.class);

            PromptTemplate promptTemplate = new PromptTemplate(sentimentPrompt);
            Map<String, Object> params = new HashMap<>();
            params.put("ticker", ticker);
            params.put("current_date", LocalDate.now().toString());
            params.put("format", stockSentimentOutputConverter.getFormat());
            Prompt prompt = promptTemplate.create(params);

            String result = llmService.callLlmWithSearch(prompt.getContents(), questionId, ticker);
            StockSentiment stockSentiment = stockSentimentOutputConverter.convert(result);

            LocalDateTime now = LocalDateTime.now();
            stockSentiment.setDate(now);

            questionAnswer.setAnswer(stockSentiment.toJson());
            questionAnswer.setStatus(QuestionAnswerStatus.COMPLETED);
            questionAnswer.setUpdatedAt(now);
            questionAnswerRepository.save(questionAnswer);

            return stockSentiment;
        } catch (Exception e) {
            logger.error("Error during sentiment generation for {}", ticker, e);
            questionAnswer.setStatus(QuestionAnswerStatus.FAILED);
            questionAnswer.setUpdatedAt(LocalDateTime.now());
            questionAnswerRepository.save(questionAnswer);
            throw e;
        }
    }
}
