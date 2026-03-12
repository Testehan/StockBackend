package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.reporting.ReportItem;
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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import com.testehan.finana.exception.InsufficientCreditException;

@Service
public class CapitalAllocationCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CapitalAllocationCalculator.class);
    public static final String EXTREMLY_BAD_NET_DEBT_TO_EBITDA = "99";

    @Value("classpath:/prompts/100Bagger/capital_allocation_prompt.txt")
    private Resource capitalAllocationPrompt;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final SecFilingRepository secFilingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LlmService llmService;

    public CapitalAllocationCalculator(CompanyOverviewRepository companyOverviewRepository, IncomeStatementRepository incomeStatementRepository, FinancialRatiosRepository financialRatiosRepository, SecFilingRepository secFilingRepository, ApplicationEventPublisher eventPublisher, LlmService llmService) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.financialRatiosRepository = financialRatiosRepository;
        this.secFilingRepository = secFilingRepository;
        this.eventPublisher = eventPublisher;
        this.llmService = llmService;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {

        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            String errorMessage = "No Company overview found for ticker " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("capitalAllocationSkill", -10, "Something went wrong and score could not be calculated ");
        }

        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);

        StringBuilder businessDescription = new StringBuilder();
        StringBuilder riskFactors = new StringBuilder();
        StringBuilder managementDiscussion = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                            riskFactors.append(latestTenKFiling.getRiskFactors());
                            managementDiscussion.append(latestTenKFiling.getManagementDiscussion());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get business description."));
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get business description."));
        });

        int shareScore;
        int debtScore;
        var netDebtToEbitda = calculateNetDebtToEbitda(ticker);
        StringBuilder netDebtToEbitdaExplanation = new StringBuilder();
        if (netDebtToEbitda.doubleValue() <= 0) {
            // Net Cash Position (Antifragile)
            netDebtToEbitdaExplanation.append("Net cash position as net debt / ebitda is ").append(netDebtToEbitda.doubleValue());
            debtScore = 2;
        } else if (netDebtToEbitda.doubleValue() < 2.5) {
            // Safe (< 2.5x)
            netDebtToEbitdaExplanation.append("Pretty safe as debt / ebitda is ").append(netDebtToEbitda.doubleValue());
            debtScore = 2;
        } else if (netDebtToEbitda.doubleValue() < 3.5) {
            // Constrained (2.5x - 3.5x)
            netDebtToEbitdaExplanation.append("Constrained as net debt / ebitda is ").append(netDebtToEbitda.doubleValue());
            debtScore = 1;
        } else {
            // Dangerous (> 3.5x)
            netDebtToEbitdaExplanation.append("Kind of dangerous as net debt / ebitda is ").append(netDebtToEbitda.doubleValue());
            debtScore = 0;
        }

        var shareCountCagr = calculateSharesOutstandingCAGR(ticker);
        StringBuilder shareCountCagrExplanation = new StringBuilder();
        // --- 1. The Cannibal Score (Max 4 pts) ---
        // Previous threshold -0.005 (-0.5%) becomes -0.5
        if (shareCountCagr.doubleValue() < -0.5) {
            // Net Buybacks (> 0.5% reduction/year)
            shareCountCagrExplanation.append(" Share count CAGR is net buybacks of ").append(shareCountCagr.doubleValue()).append(" over the last 3-5 years.");
            shareScore = 4;
        }
        // Previous threshold 0.01 (1%) becomes 1.0
        else if (shareCountCagr.doubleValue() <= 1.0) {
            // Flat to slight dilution (up to 1%)
            shareCountCagrExplanation.append(" Share count CAGR is flat to slight dilution: ").append(shareCountCagr.doubleValue()).append(" over the last 3-5 years.");
            shareScore = 3;
        }
        // Previous threshold 0.03 (3%) becomes 3.0
        else if (shareCountCagr.doubleValue() <= 3.0) {
            // Moderate dilution (Standard Tech SBC, 1-3%)
            shareCountCagrExplanation.append(" Share count CAGR is a standard tech stock based compensation : ").append(shareCountCagr.doubleValue()).append(" over the last 3-5 years.");
            shareScore = 1;
        }
        else {
            // Heavy dilution (> 3%)
            shareCountCagrExplanation.append(" Share count CAGR is a heavy dilution: ").append(shareCountCagr.doubleValue()).append(" over the last 3-5 years.");
            shareScore = 0;
        }


        PromptTemplate promptTemplate = new PromptTemplate(capitalAllocationPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        var llmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("share_cagr_percentage", shareCountCagr);
        promptParameters.put("leverage_ratio", netDebtToEbitda);
        promptParameters.put("business_description", businessDescription);
        promptParameters.put("risk_factors", riskFactors);
        promptParameters.put("mda", managementDiscussion);
        promptParameters.put("format", llmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for capital allocation analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlmWithOllama(prompt, "capital_allocation_analysis", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response with capital allocation analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = llmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("capitalAllocationSkill",
                    convertedLlmResponse.getScore() + shareScore + debtScore,
                    convertedLlmResponse.getExplanation() + netDebtToEbitdaExplanation + shareCountCagrExplanation);
        } catch (InsufficientCreditException e) {
            LOGGER.warn("Insufficient credit for operation in {}: {}", ticker, e.getMessage());
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, e));
            return new ReportItem("capitalAllocationSkill", -10, "Insufficient credit. Unable to complete analysis.");
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateCapitalAllocationSkill' failed.";
            LOGGER.error(errorMessage, e);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage, e)));
return new ReportItem("capitalAllocationSkill", -10, "Operation 'calculateCapitalAllocationSkill' failed.");
        }
    }


    private BigDecimal calculateNetDebtToEbitda(String ticker){
        Optional<FinancialRatiosData> financialRatios = financialRatiosRepository.findBySymbol(ticker);

        StringBuilder result = new StringBuilder();
        financialRatios.ifPresentOrElse(financialRatiosData -> {
            var latestAnnualReportRadios = financialRatiosData.getAnnualReports().stream()
                    .max(Comparator.comparing(tenKFiling -> tenKFiling.getDate()))
                    .get();
            if (Objects.nonNull(latestAnnualReportRadios.getNetDebtToEbitda())) {
                result.append(latestAnnualReportRadios.getNetDebtToEbitda().toPlainString());
            } else {
                result.append(EXTREMLY_BAD_NET_DEBT_TO_EBITDA);
            }

        },() -> {
            result.append(EXTREMLY_BAD_NET_DEBT_TO_EBITDA);
        });

        return new BigDecimal(result.toString());
    }

    private BigDecimal calculateSharesOutstandingCAGR(String ticker) {
        var incomeDataOptional = incomeStatementRepository.findBySymbol(ticker);
        if (incomeDataOptional.isPresent() && !Objects.isNull(incomeDataOptional.get().getAnnualReports())
                && !incomeDataOptional.get().getAnnualReports().isEmpty()) {

            var statements = incomeDataOptional.get().getAnnualReports();

            if (statements.size() < 2) {
                LOGGER.warn("Insufficient data for {}: {} years of data available, but at least 2 are required to calculate CAGR.", ticker, statements.size());
                return BigDecimal.ZERO;
            }

            statements.sort(Comparator.comparing(IncomeReport::getDate));

            List<IncomeReport> recentStatements = statements.subList(Math.max(0, statements.size() - 5), statements.size());

            BigDecimal startShares = new BigDecimal(recentStatements.get(0).getWeightedAverageShsOutDil());
            BigDecimal endShares = new BigDecimal(recentStatements.get(recentStatements.size() - 1).getWeightedAverageShsOutDil());

            double periods = recentStatements.size() - 1.0;

            if (startShares.equals(BigDecimal.ZERO)) {
                LOGGER.warn("Cannot calculate CAGR for {} because the starting shares outstanding is zero.", ticker);
                return BigDecimal.ZERO;
            }

            BigDecimal ratio = endShares.divide(startShares, MathContext.DECIMAL128);
            double cagrRaw = Math.pow(ratio.doubleValue(), 1.0 / periods) - 1.0;

            return BigDecimal.valueOf(cagrRaw * 100).setScale(2, RoundingMode.HALF_UP);
        } else {
            LOGGER.warn("No income statement data found for {}", ticker);
            return BigDecimal.ZERO;
        }
    }
}
