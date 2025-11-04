package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PricingPowerCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(PricingPowerCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final LlmService llmService;
    private final ApplicationEventPublisher eventPublisher;
    private final OptionalityCalculator optionalityCalculator;

    @Value("classpath:/prompts/pricing_power_prompt.txt")
    private Resource pricingPowerPrompt;

    public PricingPowerCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, IncomeStatementRepository incomeStatementRepository, FinancialRatiosRepository financialRatiosRepository, LlmService llmService, ApplicationEventPublisher eventPublisher, OptionalityCalculator optionalityCalculator) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.financialRatiosRepository = financialRatiosRepository;
        this.llmService = llmService;
        this.eventPublisher = eventPublisher;
        this.optionalityCalculator = optionalityCalculator;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            var errorMessage = "No Company overview found for ticker: "+ ticker;
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
            return new ReportItem("pricingPower", 0, "Something went wrong and score could not be calculated ");
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
                var errorMessage = "No 10k found for ticker: " + ticker;
                eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
                LOGGER.error(errorMessage);
            }
        }, () -> {
            var errorMessage = "No 10k found for ticker: " + ticker;
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
        });

        PromptTemplate promptTemplate = new PromptTemplate(pricingPowerPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        StringBuilder financialTable = new StringBuilder();
        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);
        Optional<FinancialRatiosData> financialRatiosDataOptional = financialRatiosRepository.findBySymbol(ticker);
        if (incomeStatementDataOptional.isEmpty() || financialRatiosDataOptional.isEmpty()) {
            var errorMessage = "No income statement data found for ticker: " + ticker;
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
        } else {
            var incomeStatementData = incomeStatementDataOptional.get();
            var financialRatiosData = financialRatiosDataOptional.get();

            List<String> lastFiveYears = incomeStatementData.getAnnualReports().stream()
                    .map(IncomeReport::getDate)
                    .filter(Objects::nonNull)                    // safety
                    .distinct()                                  // remove duplicates
                    .sorted(Comparator.reverseOrder())          // newest first
                    .limit(5)                                    // take only 5
                    .collect(Collectors.toList());

            lastFiveYears.forEach(year -> financialTable.append(year + " "));
            financialTable.append("\n");

            // Step 2: Filter the original stream to keep only reports from those years
            incomeStatementData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getDate()))
                    .forEach(incomeReport -> {
                        financialTable.append(incomeReport.getRevenue() + " ");
                    });

            financialTable.append("\n");
            incomeStatementData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getDate()))
                    .forEach(incomeReport -> {
                        financialTable.append(incomeReport.getSellingGeneralAndAdministrativeExpenses() + " ");
                    });

            financialTable.append("\n");
            financialRatiosData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getDate()))
                    .forEach(ratiosReport -> {
                        financialTable.append(ratiosReport.getGrossProfitMargin() + " ");
                    });

            financialTable.append("\n");
            financialRatiosData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getDate()))
                    .forEach(ratiosReport -> {
                        financialTable.append(ratiosReport.getEbitdaMargin() + " ");
                    });

            promptParameters.put("financial_table", financialTable);
        }

        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());

        promptParameters.put("business_description", businessDescription.toString());
        promptParameters.put("management_discussion", managementDiscussion.toString());
        promptParameters.put("latest_earnings_transcript", optionalityCalculator.getLatestEarningsTranscript(ticker));

        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for pricing power analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response for pricing power analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("pricingPower", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculatePricingPower' failed.";
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
            return new ReportItem("pricingPower", -10, "Operation 'calculatePricingPower' failed.");
        }
    }
}
