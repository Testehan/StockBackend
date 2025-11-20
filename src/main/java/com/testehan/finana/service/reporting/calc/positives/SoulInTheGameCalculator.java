package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
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
    private final ApplicationEventPublisher eventPublisher;

    public SoulInTheGameCalculator(CompanyOverviewRepository companyOverviewRepository, LlmService llmService, ApplicationEventPublisher eventPublisher) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.llmService = llmService;
        this.eventPublisher = eventPublisher;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            var errorMessage = "No Company overview found for ticker: " + ticker;
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
            return new ReportItem("soulInTheGame", -10, "Something went wrong and score could not be calculated ");
        }

        PromptTemplate promptTemplate = new PromptTemplate(soulInTheGamePrompt);
        Map<String, Object> promptParameters = new HashMap<>();
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);
        promptParameters.put("company_description", companyOverview.get().getDescription());
        promptParameters.put("company_ceo", companyOverview.get().getCeo());
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for soul in the game analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response with soul in the game analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("soulInTheGame", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateSoulInTheGame' failed.";
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
            return new ReportItem("soulInTheGame", -10, "Operation 'calculateSoulInTheGame' failed.");
        }
    }
}
