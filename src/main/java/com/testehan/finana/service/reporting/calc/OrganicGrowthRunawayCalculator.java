package com.testehan.finana.service.reporting.calc;

import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.SecFiling;
import com.testehan.finana.model.llm.responses.FerolLlmResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.SecFilingRepository;
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

import java.math.BigDecimal;
import java.util.*;

@Service
public class OrganicGrowthRunawayCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrganicGrowthRunawayCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final LlmService llmService;
    private final FerolSseService ferolSseService;
    private final OptionalityCalculator optionalityCalculator;


    @Value("classpath:/prompts/organic_growth_runaway_prompt.txt")
    private Resource organicGrowthPrompt;

    public OrganicGrowthRunawayCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, LlmService llmService, FerolSseService ferolSseService, OptionalityCalculator optionalityCalculator) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.llmService = llmService;
        this.ferolSseService = ferolSseService;
        this.optionalityCalculator = optionalityCalculator;
    }

    public FerolReportItem calculate(String ticker, BigDecimal revenueCAGRPerShare, BigDecimal sustainableGrowthRate, SseEmitter sseEmitter) {
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);

        StringBuilder stringBuilder = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getManagementDiscussion());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
        });

        var latestEarningsTranscript = optionalityCalculator.getLatestEarningsTranscript(ticker);
        ;

        PromptTemplate promptTemplate = new PromptTemplate(organicGrowthPrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("management_discussion", stringBuilder.toString());
        promptParameters.put("latest_earnings_transcript", latestEarningsTranscript);
        promptParameters.put("revenue_cagr_share", revenueCAGRPerShare);
        promptParameters.put("sustainable_growth_rate", sustainableGrowthRate);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            ferolSseService.sendSseEvent(sseEmitter, "Sending data to LLM for organic growth runaway analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            ferolSseService.sendSseEvent(sseEmitter, "Received LLM response for organic growth runaway analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("organicGrowthRunway", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateOrganicGrowthRunaway' failed.";
            LOGGER.error(errorMessage, e);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new FerolReportItem("organicGrowthRunway", -10, "Operation 'calculateOrganicGrowthRunaway' failed.");
        }
    }
}
