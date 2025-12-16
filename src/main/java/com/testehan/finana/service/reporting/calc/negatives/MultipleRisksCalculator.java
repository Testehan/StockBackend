package com.testehan.finana.service.reporting.calc.negatives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.llm.responses.FerolNegativesAnalysisLlmResponse;
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
public class MultipleRisksCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultipleRisksCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final LlmService llmService;
    private final ApplicationEventPublisher eventPublisher;
// todo maybe i should also add here the latest q&a with analysts and investors from db..
    @Value("classpath:/prompts/negatives_prompt.txt")
    private Resource multipleNegativesPrompt;

    public MultipleRisksCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, LlmService llmService, ApplicationEventPublisher eventPublisher) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.llmService = llmService;
        this.eventPublisher = eventPublisher;
    }

    public FerolNegativesAnalysisLlmResponse calculate(String ticker, SseEmitter sseEmitter) {
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);

        PromptTemplate promptTemplate = new PromptTemplate(multipleNegativesPrompt);
        Map<String, Object> promptParameters = new HashMap<>();
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolNegativesAnalysisLlmResponse.class);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat()) ;
        if (companyOverview.isPresent()){
            promptParameters.put("company_name", companyOverview.get().getCompanyName());
        }

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            promptParameters.put("business_description", latestTenKFiling.getBusinessDescription());
                            promptParameters.put("risk_factors", latestTenKFiling.getRiskFactors());
                            promptParameters.put("management_discussion", latestTenKFiling.getManagementDiscussion());

                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get business description."));
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get business description."));
        });

        if (!promptParameters.containsKey("business_description") || !promptParameters.containsKey("risk_factors")
                || !promptParameters.containsKey("management_discussion") ) {
            return new FerolNegativesAnalysisLlmResponse(-10, "Operation 'calculateMultipleRisks' failed.");
        }


        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for moat analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt, "multiple_risks_analysis", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response for moat analysis."));
            return ferolLlmResponseOutputConverter.convert(llmResponse);

        } catch (Exception e) {
            String errorMessage = "Operation 'calculateMultipleRisks' failed.";
            LOGGER.error(errorMessage, e);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage, e)));
            return new FerolNegativesAnalysisLlmResponse(-10, "Operation 'calculateMultipleRisks' failed.");
        }
    }
}
