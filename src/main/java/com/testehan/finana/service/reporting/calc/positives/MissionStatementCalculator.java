package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.SecFiling;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@Service
public class MissionStatementCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MissionStatementCalculator.class);

    @Value("classpath:/prompts/mission_statement_prompt.txt")
    private Resource missionStatementPrompt;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final LlmService llmService;
    private final ApplicationEventPublisher eventPublisher;

    public MissionStatementCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, LlmService llmService, ApplicationEventPublisher eventPublisher) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.llmService = llmService;
        this.eventPublisher = eventPublisher;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            var errorMessage = "No Company overview found for ticker: " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("missionStatement", -10, "Something went wrong and score could not be calculated ");
        }
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder businessDescription = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                var errorMessage = "No 10k found for ticker: " + ticker;
                LOGGER.warn(errorMessage);
                eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            }
        }, () -> {
            var errorMessage = "No 10k found for ticker: " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
        });

        PromptTemplate promptTemplate = new PromptTemplate(missionStatementPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        promptParameters.put("company_description", companyOverview.get().getDescription());
        promptParameters.put("business_description", businessDescription.toString());
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for mission statement analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response with mission statement analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("missionStatement", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateMissionStatement' failed.";
            LOGGER.error(errorMessage, e);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("missionStatement", -10, "Operation 'calculateMissionStatement' failed.");
        }
    }

}
