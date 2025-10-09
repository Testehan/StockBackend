package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.ReportType;
import com.testehan.finana.model.llm.responses.FerolLlmResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class InsiderOwnershipCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(InsiderOwnershipCalculator.class);

    @Value("classpath:/prompts/inside_ownership_prompt.txt")
    private Resource insiderOwnershipPrompt;

    @Value("classpath:/prompts/100Bagger/inside_ownership_prompt.txt")
    private Resource insiderOwnership100BaggerPrompt;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final LlmService llmService;
    private final ChecklistSseService checklistSseService;

    public InsiderOwnershipCalculator(CompanyOverviewRepository companyOverviewRepository, LlmService llmService, ChecklistSseService checklistSseService) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.llmService = llmService;
        this.checklistSseService = checklistSseService;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter, ReportType reportType) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new ReportItem("insideOwnership", -10, "Something went wrong and score could not be calculated ");
        }

        PromptTemplate promptTemplate = getReportTemplateBy(reportType);
        Map<String, Object> promptParameters = new HashMap<>();

        var llmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("format", llmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            checklistSseService.sendSseEvent(sseEmitter, "Sending data to LLM for insider ownership analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            checklistSseService.sendSseEvent(sseEmitter, "Received LLM response with insider ownership analysis.");
            FerolLlmResponse convertedLlmResponse = llmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("insideOwnership", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateinsideOwnership' failed.";
            LOGGER.error(errorMessage, e);
            checklistSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new ReportItem("insideOwnership", -10, "Operation 'calculateinsideOwnership' failed.");
        }
    }

    private PromptTemplate getReportTemplateBy(ReportType reportType){
        switch (reportType) {
            case FEROL -> {
                return new PromptTemplate(insiderOwnershipPrompt);
            }
            case ONE_HUNDRED_BAGGER -> {
                return new PromptTemplate(insiderOwnership100BaggerPrompt);
            }
        }
        throw new IllegalArgumentException("Invalid report type: " + reportType);
    }
}

