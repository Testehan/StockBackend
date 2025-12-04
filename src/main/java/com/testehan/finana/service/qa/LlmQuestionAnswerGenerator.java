package com.testehan.finana.service.qa;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.qa.Question;
import com.testehan.finana.model.qa.QuestionAnswer;
import com.testehan.finana.model.qa.QuestionAnswerStatus;
import com.testehan.finana.model.qa.QuestionConstants;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.QuestionAnswerRepository;
import com.testehan.finana.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.testehan.finana.model.qa.QuestionConstants.isDamodaranQuestion;
import static com.testehan.finana.model.qa.QuestionConstants.isGuruQuestion;

@Service
public class LlmQuestionAnswerGenerator {

    private static final Logger logger = LoggerFactory.getLogger(LlmQuestionAnswerGenerator.class);

    private final LlmService llmService;
    private final CompanyOverviewRepository companyOverviewRepository;
    private final QuestionAnswerRepository questionAnswerRepository;

    @Value("classpath:/prompts/qa/questions_prompt.txt")
    private Resource questionPrompt;

    @Value("classpath:/prompts/qa/guru_prompt.txt")
    private Resource guruPrompt;

    public LlmQuestionAnswerGenerator(LlmService llmService, CompanyOverviewRepository companyOverviewRepository, QuestionAnswerRepository questionAnswerRepository) {
        this.llmService = llmService;
        this.companyOverviewRepository = companyOverviewRepository;
        this.questionAnswerRepository = questionAnswerRepository;
    }

    @Async
    public void generateAnswerStreaming(String stockId, String questionId, String promptVersion, String llmModel, SseEmitter emitter) {
        String questionText = QuestionConstants.getQuestionText(questionId);
        boolean isGuruQuestion = isGuruQuestion(questionId);

        logger.info("Starting streaming answer generation for stockId: {}, questionId: {}, isGuru: {}", stockId, questionId, isGuruQuestion);
        try {
            Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(stockId);
            if (companyOverview.isEmpty()){
                logger.warn("No Company overview found for ticker: {}", stockId);
                emitter.completeWithError(new RuntimeException("No Company overview found for ticker: " + stockId));
                return;
            }

            Resource promptToUse = isGuruQuestion ? guruPrompt : questionPrompt;
            PromptTemplate promptTemplate = new PromptTemplate(promptToUse);
            Map<String, Object> promptParameters = new HashMap<>();

            promptParameters.put("question", questionText);
            promptParameters.put("company_name", companyOverview.get().getCompanyName());
            promptParameters.put("company_url", companyOverview.get().getWebsite());
            if (isGuruQuestion) {
                promptParameters.put("guru_name", QuestionConstants.getGuruNameForQuestion(questionId).orElse(""));
                promptParameters.put("current_date", java.time.LocalDate.now().toString());
                
                // Add rolling context for Damodaran sequential questions
                if (isDamodaranQuestion(questionId)) {
                    String previousContext = buildDamodaranContext(stockId, questionId, llmModel);
                    promptParameters.put("previous_context", previousContext);
                    logger.info("Built context for Damodaran question {} with {} previous answers", 
                                questionId, countPreviousAnswers(stockId, questionId, llmModel));
                } else {
                    promptParameters.put("previous_context", "This is an independent question. No previous context needed.");
                }
            }
            Prompt prompt = promptTemplate.create(promptParameters);

            String generationDate = "Generation Date: " + LocalDateTime.now() + "\n\n\n";
            StringBuilder completeAnswer = new StringBuilder(generationDate);
            emitter.send(SseEmitter.event().data(generationDate));
            llmService.streamLlmWithSearch(prompt, questionId, stockId)
                    .doOnNext(chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                            completeAnswer.append(chunk);
                        } catch (IOException e) {
                            logger.warn("Error sending SSE event", e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            emitter.send(SseEmitter.event().name("COMPLETED").data("")); // Send COMPLETED event
                        } catch (IOException e) {
                            logger.warn("Error sending COMPLETED SSE event", e);
                        }

                        Optional<QuestionAnswer> optionalQuestionAnswer = questionAnswerRepository
                                .findByStockIdAndQuestionIdAndPromptVersionAndModel(stockId, questionId, promptVersion, llmModel);

                        if (optionalQuestionAnswer.isPresent()) {
                            QuestionAnswer questionAnswer = optionalQuestionAnswer.get();
                            questionAnswer.setAnswer(completeAnswer.toString());
                            questionAnswer.setStatus(QuestionAnswerStatus.COMPLETED);
                            questionAnswer.setUpdatedAt(LocalDateTime.now());
                            questionAnswerRepository.save(questionAnswer);
                            logger.info("Completed streaming answer generation and updated record for stockId: {}, questionId: {}", stockId, questionId);
                        } else {
                            logger.warn("QuestionAnswer record not found for async update after LLM call for stockId: {}, questionId: {}", stockId, questionId);
                        }
                        emitter.complete();
                    })
                    .doOnError(error -> {
                        logger.error("Error during streaming answer generation", error);
                        Optional<QuestionAnswer> optionalQuestionAnswer = questionAnswerRepository
                                .findByStockIdAndQuestionIdAndPromptVersionAndModel(stockId, questionId, promptVersion, llmModel);
                        optionalQuestionAnswer.ifPresent(questionAnswer -> {
                            questionAnswer.setStatus(QuestionAnswerStatus.FAILED);
                            questionAnswerRepository.save(questionAnswer);
                        });
                        emitter.completeWithError(error);
                    })
                    .subscribe();

        } catch (Exception e) {
            logger.error("Error during streaming answer generation for stockId: {}, questionId: {}: {}", stockId, questionId, e.getMessage());
            emitter.completeWithError(e);
        }
    }

    @Async
    public void generateAnswerAsync(String stockId, String questionId, String promptVersion, String llmModel, String questionText) {
        logger.info("Starting async answer generation for stockId: {}, questionId: {}", stockId, questionId);
        try {
            Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(stockId);
            if (companyOverview.isEmpty()){
                logger.warn("No Company overview found for ticker: {}", stockId);

                // Optionally, update the status to FAILED
                Optional<QuestionAnswer> optionalQuestionAnswer = questionAnswerRepository
                        .findByStockIdAndQuestionIdAndPromptVersionAndModel(stockId, questionId, promptVersion, llmModel);
                optionalQuestionAnswer.ifPresent(questionAnswer -> {
                    questionAnswer.setStatus(QuestionAnswerStatus.FAILED); // Assuming a FAILED status exists
                    questionAnswerRepository.save(questionAnswer);
                });
            }

            PromptTemplate promptTemplate = new PromptTemplate(questionPrompt);
            Map<String, Object> promptParameters = new HashMap<>();

            promptParameters.put("question", questionText);
            promptParameters.put("company_name", companyOverview.get().getCompanyName());
            promptParameters.put("company_url", companyOverview.get().getWebsite());
            Prompt prompt = promptTemplate.create(promptParameters);

            String llmAnswer = llmService.callLlmWithSearch(prompt.getContents(), questionId, stockId);

            Optional<QuestionAnswer> optionalQuestionAnswer = questionAnswerRepository
                    .findByStockIdAndQuestionIdAndPromptVersionAndModel(stockId, questionId, promptVersion, llmModel);

            if (optionalQuestionAnswer.isPresent()) {
                QuestionAnswer questionAnswer = optionalQuestionAnswer.get();
                questionAnswer.setAnswer(llmAnswer);
                questionAnswer.setStatus(QuestionAnswerStatus.COMPLETED);
                questionAnswer.setUpdatedAt(LocalDateTime.now());
                questionAnswerRepository.save(questionAnswer);
                logger.info("Completed async answer generation and updated record for stockId: {}, questionId: {}", stockId, questionId);
            } else {
                logger.warn("QuestionAnswer record not found for async update after LLM call for stockId: {}, questionId: {}", stockId, questionId);
                // This scenario should ideally not happen if the record is created synchronously before this async call
            }
        } catch (Exception e) {
            logger.error("Error during async answer generation for stockId: {}, questionId: {}: {}", stockId, questionId, e.getMessage());
            // Optionally, update the status to FAILED
            Optional<QuestionAnswer> optionalQuestionAnswer = questionAnswerRepository
                    .findByStockIdAndQuestionIdAndPromptVersionAndModel(stockId, questionId, promptVersion, llmModel);
            optionalQuestionAnswer.ifPresent(questionAnswer -> {
                questionAnswer.setStatus(QuestionAnswerStatus.FAILED); // Assuming a FAILED status exists
                questionAnswerRepository.save(questionAnswer);
            });
        }
    }

    private String buildDamodaranContext(String stockId, String currentQuestionId, String llmModel) {
        int currentIndex = QuestionConstants.getDamodaranQuestionIndex(currentQuestionId);
        
        if (currentIndex <= 0) {
            return "None. This is the first step.";
        }
        
        List<Question> damodaranQuestions = QuestionConstants.DAMODARAN_QUESTIONS;
        StringBuilder context = new StringBuilder();
        
        for (int i = 0; i < currentIndex; i++) {
            String prevQuestionId = damodaranQuestions.get(i).getId();
            Optional<QuestionAnswer> prevAnswerOpt = questionAnswerRepository
                    .findByStockIdAndQuestionIdAndPromptVersionAndModel(stockId, prevQuestionId, "v1", llmModel);
            
            if (prevAnswerOpt.isPresent() && prevAnswerOpt.get().getStatus() == QuestionAnswerStatus.COMPLETED) {
                context.append("Q").append(i + 1).append(": ")
                       .append(damodaranQuestions.get(i).getText())
                       .append("\nA: ")
                       .append(prevAnswerOpt.get().getAnswer())
                       .append("\n\n");
            }
        }
        
        return context.length() > 0 ? context.toString() : "None. Previous answers not available yet. Please answer based on general valuation principles.";
    }

    private int countPreviousAnswers(String stockId, String currentQuestionId, String llmModel) {
        int currentIndex = QuestionConstants.getDamodaranQuestionIndex(currentQuestionId);
        if (currentIndex <= 0) return 0;
        
        List<Question> damodaranQuestions = QuestionConstants.DAMODARAN_QUESTIONS;
        int count = 0;
        
        for (int i = 0; i < currentIndex; i++) {
            String prevQuestionId = damodaranQuestions.get(i).getId();
            Optional<QuestionAnswer> prevAnswerOpt = questionAnswerRepository
                    .findByStockIdAndQuestionIdAndPromptVersionAndModel(stockId, prevQuestionId, "v1", llmModel);
            
            if (prevAnswerOpt.isPresent() && prevAnswerOpt.get().getStatus() == QuestionAnswerStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    // TODO i think this may be removed..
    private String truncateAnswer(String answer) {
        if (answer == null || answer.length() <= 500) {
            return answer;
        }
        return answer.substring(0, 500) + "... [truncated]";
    }
}
