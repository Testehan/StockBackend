package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.filing.SecFiling;
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

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class CyclicalityCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CyclicalityCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final LlmService llmService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("classpath:/prompts/customers_cyclicality.txt")
    private Resource companyCyclicalityPrompt;

    public CyclicalityCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, IncomeStatementRepository incomeStatementRepository, FinancialRatiosRepository financialRatiosRepository, LlmService llmService, ApplicationEventPublisher eventPublisher) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.financialRatiosRepository = financialRatiosRepository;
        this.llmService = llmService;
        this.eventPublisher = eventPublisher;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            String errorMessage = "No Company overview found for ticker " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("companyCyclicality", -10, "Something went wrong and score could not be calculated ");
        }
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder riskFactors = new StringBuilder();
        StringBuilder businessDescription = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            riskFactors.append(latestTenKFiling.getRiskFactors());
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get risk factors."));
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get risk factors."));
        });

        PromptTemplate promptTemplate = new PromptTemplate(companyCyclicalityPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);
        if (incomeStatementDataOptional.isEmpty()) {
            String errorMessage = "No income statement data found for ticker " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
        } else {
            var incomeStatementData = incomeStatementDataOptional.get();
            StringBuilder companyRevenueTrend = new StringBuilder();
            incomeStatementData.getAnnualReports().stream()
                    .sorted(Comparator.comparing(r -> LocalDate.parse(r.getDate())))
                    .forEachOrdered(r -> companyRevenueTrend.append(r.getRevenue()).append(" -> "));

            promptParameters.put("company_revenue_trend", companyRevenueTrend.toString());
        }

        Optional<FinancialRatiosData> financialRatiosDataOptional = financialRatiosRepository.findBySymbol(ticker);
        if (financialRatiosDataOptional.isEmpty()) {
            String errorMessage = "No financial ratios data found for ticker " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
        } else {
            var financialRatiosData = financialRatiosDataOptional.get();
            StringBuilder operatingMarginTrend = new StringBuilder();
            financialRatiosData.getAnnualReports().stream()
                    .sorted(Comparator.comparing(r -> LocalDate.parse(r.getDate())))
                    .forEachOrdered(r -> operatingMarginTrend.append(r.getOperatingProfitMargin()).append(" -> "));

            promptParameters.put("company_operating_margin_trend", operatingMarginTrend.toString());
        }

        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("company_industry", companyOverview.get().getIndustry());
        promptParameters.put("company_beta", companyOverview.get().getBeta());

        promptParameters.put("business_description", businessDescription.toString());
        promptParameters.put("risk_factors", riskFactors.toString());

        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for company cyclicality analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlmWithOllama(prompt, "cyclicality_analysis", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response for company cyclicality analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("companyCyclicality", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateCyclicality' failed.";
            LOGGER.error(errorMessage, e);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage, e)));
            return new ReportItem("companyCyclicality", -10, "Operation 'calculateCyclicality' failed.");
        }
    }
}
