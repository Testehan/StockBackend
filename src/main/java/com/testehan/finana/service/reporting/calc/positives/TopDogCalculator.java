package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.filing.SecFiling;
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class TopDogCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TopDogCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final LlmService llmService;
    private final ApplicationEventPublisher eventPublisher;
    private final OptionalityCalculator optionalityCalculator;

    @Value("classpath:/prompts/top_dog_prompt.txt")
    private Resource topDogPrompt;

    public TopDogCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, LlmService llmService, ApplicationEventPublisher eventPublisher, OptionalityCalculator optionalityCalculator) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.llmService = llmService;
        this.eventPublisher = eventPublisher;
        this.optionalityCalculator = optionalityCalculator;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder stringBuilder = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get business description."));
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get business description."));
        });

        var latestEarningsTranscript = optionalityCalculator.getLatestEarningsTranscript(ticker);

        PromptTemplate promptTemplate = new PromptTemplate(topDogPrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("company_name", companyOverview.map(CompanyOverview::getCompanyName).orElse("unknown")); // Added null check
        promptParameters.put("business_description", stringBuilder.toString());
        promptParameters.put("latest_earnings_transcript", latestEarningsTranscript);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for top dog or first mover analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlmWithOllama(prompt, "topdog_firstmover_analysis", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response for top dog or first mover analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("topDogFirstMover", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateTopDogOrFirstMover' failed.";
            LOGGER.error(errorMessage, e);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage, e)));
            return new ReportItem("topDogFirstMover", -10, "Operation 'calculateTopDogOrFirstMover' failed.");
        }
    }
}
