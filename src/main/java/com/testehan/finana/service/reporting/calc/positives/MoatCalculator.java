package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.SecFiling;
import com.testehan.finana.model.llm.responses.FerolLlmResponse;
import com.testehan.finana.model.llm.responses.FerolMoatAnalysisLlmResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.ChecklistSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class MoatCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoatCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final LlmService llmService;
    private final ChecklistSseService checklistSseService;

    @Value("classpath:/prompts/moat_prompt.txt")
    private Resource moatPrompt;

    @Value("classpath:/prompts/100Bagger/moat_prompt.txt")
    private Resource moat100BaggerPrompt;

    public MoatCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, LlmService llmService, ChecklistSseService checklistSseService) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.llmService = llmService;
        this.checklistSseService = checklistSseService;
    }

    public FerolMoatAnalysisLlmResponse calculate(String ticker, SseEmitter sseEmitter) {
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);

        StringBuilder stringBuilder = new StringBuilder();

        companyOverview.ifPresent( overview -> {
            stringBuilder.append(overview.getCompanyName());
            stringBuilder.append(overview.getDescription()).append("\n");
        });

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                checklistSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            checklistSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
        });


        PromptTemplate promptTemplate = new PromptTemplate(moatPrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolMoatAnalysisLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("business_description", stringBuilder.toString());

        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            checklistSseService.sendSseEvent(sseEmitter, "Sending data to LLM for moat analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            checklistSseService.sendSseEvent(sseEmitter, "Received LLM response for moat analysis.");
            return ferolLlmResponseOutputConverter.convert(llmResponse);

        } catch (Exception e) {
            String errorMessage = "Operation 'calculateMoats' failed.";
            LOGGER.error(errorMessage, e);
            checklistSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new FerolMoatAnalysisLlmResponse(-10, "Operation 'calculateMoats' failed.");
        }
    }

    public ReportItem calculate100BaggerMoat(String ticker, SseEmitter sseEmitter) {
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);

        StringBuilder stringBuilder = new StringBuilder();

        companyOverview.ifPresent( overview -> {
            stringBuilder.append(overview.getCompanyName());
            stringBuilder.append(overview.getDescription()).append("\n");
        });

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                checklistSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            checklistSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
        });


        PromptTemplate promptTemplate = new PromptTemplate(moat100BaggerPrompt);
        var llmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("business_description", stringBuilder.toString());

        promptParameters.put("format", llmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            checklistSseService.sendSseEvent(sseEmitter, "Sending data to LLM for moat analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            checklistSseService.sendSseEvent(sseEmitter, "Received LLM response for moat analysis.");
            FerolLlmResponse convertedLlmResponse = llmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("competitiveAdvantageMoat", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());

        } catch (Exception e) {
            String errorMessage = "Operation 'calculateMoats' failed.";
            LOGGER.error(errorMessage, e);
            checklistSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new ReportItem("competitiveAdvantageMoat",-10, "Operation 'calculateMoats' failed.");
        }
    }
}
