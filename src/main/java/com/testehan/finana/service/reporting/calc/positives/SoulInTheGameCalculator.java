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
public class SoulInTheGameCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoulInTheGameCalculator.class);

    @Value("classpath:/prompts/soul_in_game_prompt.txt")
    private Resource soulInTheGamePrompt;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final LlmService llmService;
    private final FerolSseService ferolSseService;

    public SoulInTheGameCalculator(CompanyOverviewRepository companyOverviewRepository, LlmService llmService, FerolSseService ferolSseService) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.llmService = llmService;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            ferolSseService.sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new FerolReportItem("soulInTheGame", -10, "Something went wrong and score could not be calculated ");
        }

        PromptTemplate promptTemplate = new PromptTemplate(soulInTheGamePrompt);
        Map<String, Object> promptParameters = new HashMap<>();
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);
        promptParameters.put("company_description", companyOverview.get().getDescription());
        promptParameters.put("company_ceo", companyOverview.get().getCeo());
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            ferolSseService.sendSseEvent(sseEmitter, "Sending data to LLM for soul in the game analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlmLast(prompt);
            ferolSseService.sendSseEvent(sseEmitter, "Received LLM response with soul in the game analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("soulInTheGame", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateSoulInTheGame' failed.";
            LOGGER.error(errorMessage, e);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new FerolReportItem("soulInTheGame", -10, "Operation 'calculateSoulInTheGame' failed.");
        }
    }
}
