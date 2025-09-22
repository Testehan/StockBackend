package com.testehan.finana.service.reporting.calc;

import com.testehan.finana.model.CompanyOverview;
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
public class OperatingLeverageCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperatingLeverageCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final LlmService llmService;
    private final FerolSseService ferolSseService;

    @Value("classpath:/prompts/operating_leverage_prompt.txt")
    private Resource operatingLeveragePrompt;

    public OperatingLeverageCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, LlmService llmService, FerolSseService ferolSseService) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.llmService = llmService;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, String opexAsPercentageOfRevenueTrend, BigDecimal revenueCAGR3y, double expectedRevenueGrowth, SseEmitter sseEmitter) {
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
                ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
        });

        PromptTemplate promptTemplate = new PromptTemplate(operatingLeveragePrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("business_description", stringBuilder.toString());
        promptParameters.put("rev_cagr_3y", revenueCAGR3y);
        promptParameters.put("expected_rev_growth", expectedRevenueGrowth);
        promptParameters.put("opex_to_rev_list", opexAsPercentageOfRevenueTrend);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            ferolSseService.sendSseEvent(sseEmitter, "Sending data to LLM for operating leverage analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            ferolSseService.sendSseEvent(sseEmitter, "Received LLM response for operating leverage analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("operatingLeverage", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateOperatingLeverage' failed.";
            LOGGER.error(errorMessage, e);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new FerolReportItem("operatingLeverage", -10, "Operation 'calculateOperatingLeverage' failed.");
        }
    }
}
