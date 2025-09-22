package com.testehan.finana.service.reporting.calc;

import com.testehan.finana.model.BalanceSheetData;
import com.testehan.finana.model.BalanceSheetReport;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.RevenueSegmentationData;
import com.testehan.finana.model.RevenueSegmentationReport;
import com.testehan.finana.model.SecFiling;
import com.testehan.finana.model.llm.responses.FerolLlmResponse;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.RevenueSegmentationDataRepository;
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RecurringRevenueCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecurringRevenueCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final LlmService llmService;
    private final FerolSseService ferolSseService;

    @Value("classpath:/prompts/recurring_revenue_prompt.txt")
    private Resource recurringRevenuePrompt;

    public RecurringRevenueCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, RevenueSegmentationDataRepository revenueSegmentationDataRepository, BalanceSheetRepository balanceSheetRepository, LlmService llmService, FerolSseService ferolSseService) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.revenueSegmentationDataRepository = revenueSegmentationDataRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.llmService = llmService;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            ferolSseService.sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new FerolReportItem("recurringRevenue", 0, "Something went wrong and score could not be calculated ");
        }
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder businessDescription = new StringBuilder();
        StringBuilder managementDiscussion = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            managementDiscussion.append(latestTenKFiling.getManagementDiscussion());
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                ferolSseService.sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            ferolSseService.sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
        });

        PromptTemplate promptTemplate = new PromptTemplate(recurringRevenuePrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        Optional<RevenueSegmentationData> revenueSegmentationOptional = revenueSegmentationDataRepository.findBySymbol(ticker);
        if (revenueSegmentationOptional.isEmpty()) {
            LOGGER.warn("No revenue segmentation data found for ticker: {}", ticker);
            ferolSseService.sendSseErrorEvent(sseEmitter, "No revenue segmentation data found for ticker " + ticker);
        } else {
            var revenueSegmentationData = revenueSegmentationOptional.get();

            List<Integer> lastFiveYears = revenueSegmentationData.getAnnualReports().stream()
                    .map(RevenueSegmentationReport::getFiscalYear)
                    .filter(Objects::nonNull)                    // safety
                    .distinct()                                  // remove duplicates
                    .sorted(Comparator.reverseOrder())          // newest first
                    .limit(5)                                    // take only 5
                    .collect(Collectors.toList());

            // Step 2: Filter the original stream to keep only reports from those years
            List<RevenueSegmentationReport> lastFiveYearsReports = revenueSegmentationData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getFiscalYear()))
                    .collect(Collectors.toList());

            promptParameters.put("revenue_segmentation", lastFiveYearsReports);
        }

        Optional<BalanceSheetData> balancesheetDataOptional = balanceSheetRepository.findBySymbol(ticker);
        if (balancesheetDataOptional.isEmpty()) {
            LOGGER.warn("No balance sheet data found for ticker: {}", ticker);
            ferolSseService.sendSseErrorEvent(sseEmitter, "No balance sheet data found for ticker " + ticker);
        } else {
            var balancesheetData = balancesheetDataOptional.get();
            List<String> lastFiveQuarters = balancesheetData.getQuarterlyReports().stream()
                    .map(BalanceSheetReport::getDate)
                    .filter(Objects::nonNull)                    // safety
                    .distinct()                                  // remove duplicates
                    .sorted(Comparator.reverseOrder())          // newest first
                    .limit(5)                                    // take only 5
                    .sorted()                                    // optional: return in ascending order [2021,2022,2023,2024,2025]
                    .collect(Collectors.toList());

            List<String> lastQuartersDeferredRevenue= balancesheetData.getQuarterlyReports().stream()
                    .filter(r ->  lastFiveQuarters.contains(r.getDate()))
                    .map(BalanceSheetReport :: getDeferredRevenue)
                    .collect(Collectors.toList());

            promptParameters.put("deferredRevenue_last_5_quarters", lastQuartersDeferredRevenue);
        }

        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());

        promptParameters.put("business_description", businessDescription.toString());
        promptParameters.put("management_discussion", managementDiscussion.toString());

        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            ferolSseService.sendSseEvent(sseEmitter, "Sending data to LLM for recurring revenue analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            ferolSseService.sendSseEvent(sseEmitter, "Received LLM response for recurring revenue analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("recurringRevenue", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateRecurringRevenue' failed.";
            LOGGER.error(errorMessage, e);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new FerolReportItem("recurringRevenue", -10, "Operation 'calculateRecurringRevenue' failed.");
        }
    }
}
