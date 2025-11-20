package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.finstatement.BalanceSheetData;
import com.testehan.finana.model.finstatement.BalanceSheetReport;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.finstatement.RevenueSegmentationData;
import com.testehan.finana.model.finstatement.RevenueSegmentationReport;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.RevenueSegmentationDataRepository;
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
    private final ApplicationEventPublisher eventPublisher;

    @Value("classpath:/prompts/recurring_revenue_prompt.txt")
    private Resource recurringRevenuePrompt;

    public RecurringRevenueCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, RevenueSegmentationDataRepository revenueSegmentationDataRepository, BalanceSheetRepository balanceSheetRepository, LlmService llmService, ApplicationEventPublisher eventPublisher) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.revenueSegmentationDataRepository = revenueSegmentationDataRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.llmService = llmService;
        this.eventPublisher = eventPublisher;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            String errorMessage = "No Company overview found for ticker " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("recurringRevenue", 0, "Something went wrong and score could not be calculated ");
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
                eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException("No 10k available to get data.")));
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException("No 10k available to get data.")));
        });

        PromptTemplate promptTemplate = new PromptTemplate(recurringRevenuePrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        Optional<RevenueSegmentationData> revenueSegmentationOptional = revenueSegmentationDataRepository.findBySymbol(ticker);
        if (revenueSegmentationOptional.isEmpty()) {
            String errorMessage = "No revenue segmentation data found for ticker " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
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
            String errorMessage = "No balance sheet data found for ticker " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
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

        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());

        promptParameters.put("business_description", businessDescription.toString());
        promptParameters.put("management_discussion", managementDiscussion.toString());

        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for recurring revenue analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response for recurring revenue analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("recurringRevenue", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateRecurringRevenue' failed.";
            LOGGER.error(errorMessage, e);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage, e)));
            return new ReportItem("recurringRevenue", -10, "Operation 'calculateRecurringRevenue' failed.");
        }
    }
}
