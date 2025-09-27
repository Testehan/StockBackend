package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.llm.responses.FerolLlmResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.FerolSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class CultureCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CultureCalculator.class);

    @Value("classpath:/prompts/culture_prompt.txt")
    private Resource culturePrompt;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final LlmService llmService;
    private final FerolSseService ferolSseService;

    public CultureCalculator(CompanyOverviewRepository companyOverviewRepository, LlmService llmService, FerolSseService ferolSseService) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.llmService = llmService;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            ferolSseService.sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new FerolReportItem("cultureRatings", -10, "Something went wrong and score could not be calculated ");
        }

        PromptTemplate promptTemplate = new PromptTemplate(culturePrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            ferolSseService.sendSseEvent(sseEmitter, "Sending data to LLM for company culture analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlmLast(prompt);
            ferolSseService.sendSseEvent(sseEmitter, "Received LLM response with company culture analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("cultureRatings", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculatecultureRatings' failed.";
            LOGGER.error(errorMessage, e);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new FerolReportItem("cultureRatings", -10, "Operation 'calculatecultureRatings' failed.");
        }
    }
}

