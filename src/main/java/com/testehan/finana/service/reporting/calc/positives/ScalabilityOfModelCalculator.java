package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.exception.InsufficientCreditException;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.finstatement.*;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.model.reporting.ReportItem;
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
public class ScalabilityOfModelCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScalabilityOfModelCalculator.class);

    @Value("classpath:/prompts/100Bagger/scalability_of_model_prompt.txt")
    private Resource scalabilityOfModelPrompt;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LlmService llmService;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final CashFlowRepository cashFlowRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;

    public ScalabilityOfModelCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, ApplicationEventPublisher eventPublisher, LlmService llmService, FinancialRatiosRepository financialRatiosRepository, CashFlowRepository cashFlowRepository, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository) {
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
            String errorMessage = "No Company overview found for ticker " + ticker;
            LOGGER.warn(errorMessage);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("scalabilityOfModel", -10, "Something went wrong and score could not be calculated ");
        }

        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);

        StringBuilder businessDescription = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get business description."));
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "No 10k available to get business description."));
        });

        PromptTemplate promptTemplate = new PromptTemplate(scalabilityOfModelPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        var llmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("business_description", businessDescription);
        promptParameters.put("avg_gross_profit_margin", calculateAverageGrossProfitMargin(ticker));
        promptParameters.put("avg_capex_intensity", calculateAverageCapexIntensity(ticker));
        promptParameters.put("revenue_cagr", calculateRevenueCAGR(ticker));
        promptParameters.put("total_assets_cagr", calculateTotalAssetsCAGR(ticker));
        promptParameters.put("format", llmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for scalability of model analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlmWithOllama(prompt, "scalability_model_analysis", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response with scalability of model analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = llmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("scalabilityOfModel",
                    convertedLlmResponse.getScore(),
                    convertedLlmResponse.getExplanation());
        } catch (InsufficientCreditException e) {
            LOGGER.warn("Insufficient credit for operation in {}: {}", ticker, e.getMessage());
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, e));
            return new ReportItem("scalabilityOfModel", -10, "Insufficient credit. Unable to complete analysis.");
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateScalabilityOfModel' failed.";
            LOGGER.error(errorMessage, e);
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage, e)));
            return new ReportItem("scalabilityOfModel", -10, "Operation 'calculateScalabilityOfModel' failed.");
        }
    }

    private BigDecimal calculateAverageGrossProfitMargin(String ticker) {
        Optional<FinancialRatiosData> financialRatiosOpt = financialRatiosRepository.findBySymbol(ticker);
        if (financialRatiosOpt.isEmpty() || financialRatiosOpt.get().getAnnualReports().isEmpty()) {
            LOGGER.warn("No financial ratios found for ticker: {}", ticker);
            return BigDecimal.ZERO;
        }

        List<FinancialRatiosReport> reports = financialRatiosOpt.get().getAnnualReports();
        reports.sort(Comparator.comparing(FinancialRatiosReport::getDate).reversed());

        List<FinancialRatiosReport> recentReports = reports.stream().limit(5).toList();

        if (recentReports.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = recentReports.stream()
                .map(FinancialRatiosReport::getGrossProfitMargin)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(recentReports.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageCapexIntensity(String ticker) {
        Optional<CashFlowData> cashFlowDataOpt = cashFlowRepository.findBySymbol(ticker);
        Optional<IncomeStatementData> incomeDataOpt = incomeStatementRepository.findBySymbol(ticker);

        if (cashFlowDataOpt.isEmpty() || cashFlowDataOpt.get().getAnnualReports().isEmpty() ||
                incomeDataOpt.isEmpty() || incomeDataOpt.get().getAnnualReports().isEmpty()) {
            LOGGER.warn("No cash flow or income data found for ticker: {}", ticker);
            return BigDecimal.ZERO;
        }

        List<CashFlowReport> cashFlowReports = cashFlowDataOpt.get().getAnnualReports();
        cashFlowReports.sort(Comparator.comparing(CashFlowReport::getDate).reversed());
        List<CashFlowReport> recentCashFlowReports = cashFlowReports.stream().limit(5).toList();

        List<IncomeReport> incomeReports = incomeDataOpt.get().getAnnualReports();
        incomeReports.sort(Comparator.comparing(IncomeReport::getDate).reversed());
        List<IncomeReport> recentIncomeReports = incomeReports.stream().limit(5).toList();

        if (recentCashFlowReports.isEmpty() || recentIncomeReports.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalCapexIntensity = BigDecimal.ZERO;
        int count = 0;
        for (CashFlowReport cashFlowReport : recentCashFlowReports) {
            for (IncomeReport incomeReport : recentIncomeReports) {
                if (cashFlowReport.getFiscalYear().equals(incomeReport.getFiscalYear())) {
                    if (incomeReport.getRevenue() != null && new BigDecimal(incomeReport.getRevenue()).compareTo(BigDecimal.ZERO) != 0 && cashFlowReport.getCapitalExpenditure() != null) {
                        BigDecimal capex = new BigDecimal(cashFlowReport.getCapitalExpenditure());
                        BigDecimal revenue = new BigDecimal(incomeReport.getRevenue());
                        totalCapexIntensity = totalCapexIntensity.add(capex.divide(revenue, 4, RoundingMode.HALF_UP));
                        count++;
                    }
                }
            }
        }

        return count == 0 ? BigDecimal.ZERO : totalCapexIntensity.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
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

    private BigDecimal calculateTotalAssetsCAGR(String ticker) {
        Optional<BalanceSheetData> balanceSheetDataOpt = balanceSheetRepository.findBySymbol(ticker);
        if (balanceSheetDataOpt.isEmpty() || balanceSheetDataOpt.get().getAnnualReports().isEmpty()) {
            LOGGER.warn("No balance sheet data found for ticker: {}", ticker);
            return BigDecimal.ZERO;
        }

        List<BalanceSheetReport> reports = balanceSheetDataOpt.get().getAnnualReports();
        reports.sort(Comparator.comparing(BalanceSheetReport::getDate));

        List<BalanceSheetReport> recentReports = reports.stream().limit(5).toList();
        if (recentReports.size() < 2) {
            return BigDecimal.ZERO;
        }

        if (recentReports.get(0).getTotalAssets() == null || recentReports.get(recentReports.size() - 1).getTotalAssets() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal startValue = new BigDecimal(recentReports.get(0).getTotalAssets());
        BigDecimal endValue = new BigDecimal(recentReports.get(recentReports.size() - 1).getTotalAssets());
        double years = recentReports.size() - 1;

        if (startValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        double cagr = Math.pow(endValue.divide(startValue, 4, RoundingMode.HALF_UP).doubleValue(), 1.0 / years) - 1.0;
        return new BigDecimal(cagr * 100).setScale(2, RoundingMode.HALF_UP);
    }
}
