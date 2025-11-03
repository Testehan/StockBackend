package com.testehan.finana.service.qa;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.qa.QuestionAnswer;
import com.testehan.finana.model.qa.QuestionAnswerStatus;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.QuestionAnswerRepository;
import com.testehan.finana.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class LlmQuestionAnswerGenerator {

    private static final Logger logger = LoggerFactory.getLogger(LlmQuestionAnswerGenerator.class);

    private final LlmService llmService;
    private final CompanyOverviewRepository companyOverviewRepository;
    private final QuestionAnswerRepository questionAnswerRepository;

    @Value("classpath:/prompts/qa/questions_prompt.txt")
    private Resource questionPrompt;

    public LlmQuestionAnswerGenerator(LlmService llmService, CompanyOverviewRepository companyOverviewRepository, QuestionAnswerRepository questionAnswerRepository) {
        this.llmService = llmService;
        this.companyOverviewRepository = companyOverviewRepository;
        this.questionAnswerRepository = questionAnswerRepository;
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

            var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(String.class);

            promptParameters.put("question", questionText);
            promptParameters.put("company_name", companyOverview.get().getCompanyName());
            promptParameters.put("company_url", companyOverview.get().getWebsite());
            promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
            Prompt prompt = promptTemplate.create(promptParameters);

            String llmAnswer = llmService.callLlm(prompt);

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
}
