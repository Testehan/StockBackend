package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.repository.*;
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
import java.math.RoundingMode;
import java.util.*;

@Service
public class GrowthCurveCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrowthCurveCalculator.class);

    @Value("classpath:/prompts/100Bagger/growth_curve_prompt.txt")
    private Resource growthCurvePrompt;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LlmService llmService;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final CashFlowRepository cashFlowRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;

    public GrowthCurveCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, ApplicationEventPublisher eventPublisher, LlmService llmService, FinancialRatiosRepository financialRatiosRepository, CashFlowRepository cashFlowRepository, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.eventPublisher = eventPublisher;
        this.llmService = llmService;
        this.financialRatiosRepository = financialRatiosRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()) {
            var errorMessage = "No Company overview found for ticker: " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("earlyGrowthCurveInflection", -10, "Something went wrong and score could not be calculated ");
        }

        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);

        StringBuilder mda = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            mda.append(latestTenKFiling.getManagementDiscussion());
                        });
            } else {
                var errorMessage = "No 10k found for ticker: " + ticker;
                LOGGER.warn(errorMessage);
                eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            }
        }, () -> {
            var errorMessage = "No 10k found for ticker: " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
        });

        PromptTemplate promptTemplate = new PromptTemplate(growthCurvePrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        var llmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        BigDecimal revenueCagr = calculateRevenueCAGR(ticker);
        BigDecimal recentRevenueGrowth = calculateRecentRevenueGrowth(ticker);
        BigDecimal accelerationDelta = recentRevenueGrowth.subtract(revenueCagr);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("mda", mda);
        promptParameters.put("revenue_cagr", revenueCagr);
        promptParameters.put("recent_revenue_growth", recentRevenueGrowth);
        promptParameters.put("acceleration_delta", accelerationDelta);
        promptParameters.put("format", llmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for growth curve analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response with growth curve analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = llmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("earlyGrowthCurveInflection",
                    convertedLlmResponse.getScore(),
                    convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateearlyGrowthCurveInflection' failed.";
            LOGGER.error(errorMessage, e);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("earlyGrowthCurveInflection", -10, "Operation 'calculateearlyGrowthCurveInflection' failed.");
        }
    }

    private BigDecimal calculateRevenueCAGR(String ticker) {
        Optional<IncomeStatementData> incomeDataOpt = incomeStatementRepository.findBySymbol(ticker);
        if (incomeDataOpt.isEmpty() || incomeDataOpt.get().getAnnualReports().isEmpty()) {
            LOGGER.warn("No income data found for ticker: {}", ticker);
            return BigDecimal.ZERO;
        }

        List<IncomeReport> reports = incomeDataOpt.get().getAnnualReports();
        reports.sort(Comparator.comparing(IncomeReport::getDate));

        List<IncomeReport> recentReports = reports.stream().limit(5).toList();
        if (recentReports.size() < 2) {
            return BigDecimal.ZERO;
        }

        if (recentReports.get(0).getRevenue() == null || recentReports.get(recentReports.size() - 1).getRevenue() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal startValue = new BigDecimal(recentReports.get(0).getRevenue());
        BigDecimal endValue = new BigDecimal(recentReports.get(recentReports.size() - 1).getRevenue());
        double years = recentReports.size() - 1;

        if (startValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        double cagr = Math.pow(endValue.divide(startValue, 4, RoundingMode.HALF_UP).doubleValue(), 1.0 / years) - 1.0;
        return new BigDecimal(cagr * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRecentRevenueGrowth(String ticker) {
        Optional<IncomeStatementData> incomeDataOpt = incomeStatementRepository.findBySymbol(ticker);
        if (incomeDataOpt.isEmpty() || incomeDataOpt.get().getQuarterlyReports().isEmpty()) {
            LOGGER.warn("No quarterly income data found for ticker: {}", ticker);
            return BigDecimal.ZERO;
        }

        List<IncomeReport> reports = incomeDataOpt.get().getQuarterlyReports();
        reports.sort(Comparator.comparing(IncomeReport::getDate).reversed());

        if (reports.size() < 6) { // Need at least 2 quarters and their counterparts from the previous year
            LOGGER.warn("Not enough quarterly reports to calculate recent revenue growth for {}. Found only {} reports.", ticker, reports.size());
            return BigDecimal.ZERO;
        }

        IncomeReport mostRecent = reports.get(0);
        IncomeReport secondMostRecent = reports.get(1);

        IncomeReport mostRecentPrevYear = reports.get(4);
        IncomeReport secondMostRecentPrevYear = reports.get(5);

        BigDecimal mostRecentGrowth = calculateYoYGrowth(mostRecent, mostRecentPrevYear);
        BigDecimal secondMostRecentGrowth = calculateYoYGrowth(secondMostRecent, secondMostRecentPrevYear);

        if (mostRecentGrowth.equals(BigDecimal.ZERO) || secondMostRecentGrowth.equals(BigDecimal.ZERO)) {
            return mostRecentGrowth.add(secondMostRecentGrowth).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }

        return mostRecentGrowth.add(secondMostRecentGrowth).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateYoYGrowth(IncomeReport current, IncomeReport previous) {
        if (current.getRevenue() == null || previous.getRevenue() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal currentRevenue = new BigDecimal(current.getRevenue());
        BigDecimal previousRevenue = new BigDecimal(previous.getRevenue());

        if (previousRevenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // Avoid division by zero
        }

        return currentRevenue.subtract(previousRevenue)
                .divide(previousRevenue, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(100)).setScale(2, RoundingMode.HALF_UP);
    }

}