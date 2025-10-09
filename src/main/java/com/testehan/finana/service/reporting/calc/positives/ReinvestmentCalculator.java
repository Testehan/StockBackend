package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.ChecklistSseService;
import com.testehan.finana.util.SafeParser;
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
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReinvestmentCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReinvestmentCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final CashFlowRepository cashFlowRepository;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final SecFilingRepository secFilingRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final LlmService llmService;
    private final ChecklistSseService checklistSseService;
    private final SafeParser safeParser;

    @Value("classpath:/prompts/100Bagger/reinvestments_prompt.txt")
    private Resource reinvestmentsPrompt;

    public ReinvestmentCalculator(CompanyOverviewRepository companyOverviewRepository, IncomeStatementRepository incomeStatementRepository, CashFlowRepository cashFlowRepository, FinancialRatiosRepository financialRatiosRepository, SecFilingRepository secFilingRepository, BalanceSheetRepository balanceSheetRepository, LlmService llmService, ChecklistSseService checklistSseService, SafeParser safeParser) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.financialRatiosRepository = financialRatiosRepository;
        this.secFilingRepository = secFilingRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.llmService = llmService;
        this.checklistSseService = checklistSseService;
        this.safeParser = safeParser;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()) {
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new ReportItem("reinvestmentCapacity", 0, "Something went wrong and score could not be calculated ");
        }

        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder managementDiscussion = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            managementDiscussion.append(latestTenKFiling.getManagementDiscussion());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                checklistSseService.sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
        });

        // Get annual ROIC for 5-year median
        Optional<FinancialRatiosData> financialRatiosData = financialRatiosRepository.findBySymbol(ticker);

        if (financialRatiosData.isEmpty() || financialRatiosData.get().getAnnualReports().isEmpty() || financialRatiosData.get().getQuarterlyReports().isEmpty()) {
            LOGGER.warn("No financial ratios data found for ticker: {}", ticker);
            checklistSseService.sendSseEvent(sseEmitter, "ROIC calculation skipped: No data found.");
            return new ReportItem("reinvestmentCapacity", 0, "No annual or quarterly financial ratios data available.");
        }

        var reinvestmentRate5y = calculateReinvestmentRate5y(ticker, sseEmitter);
        var averageRoic = calculate5yrAverageRoic(financialRatiosData);
        var averageRoe = calculate5yrAverageRoe(financialRatiosData);
        var investedCapitalChange = classifyInvestedCapitalTrend(calculateInvestedCapitalChange(ticker,sseEmitter));

        PromptTemplate promptTemplate = new PromptTemplate(reinvestmentsPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        var llmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("management_discussion", managementDiscussion.toString());

        promptParameters.put("reinvestment_rate", reinvestmentRate5y * 100);
        promptParameters.put("roic_5y", averageRoic.toPlainString());
        promptParameters.put("roe_5y", averageRoe.toPlainString());
        promptParameters.put("invested_capital_trend", investedCapitalChange);

        promptParameters.put("format", llmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            checklistSseService.sendSseEvent(sseEmitter, "Sending data to LLM for reinvestments analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            checklistSseService.sendSseEvent(sseEmitter, "Received LLM response for reinvestments analysis.");
            LlmScoreExplanationResponse convertedLlmResponse = llmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("reinvestmentCapacity", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateReinvestment' failed.";
            LOGGER.error(errorMessage, e);
            checklistSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new ReportItem("reinvestmentCapacity", -10, "Operation 'calculateReinvestment' failed.");
        }
    }

    public ReportItem calculateSustainedReturnsOnCapital(String ticker, SseEmitter sseEmitter) {
        // Get annual ROIC for 5-year median
        Optional<FinancialRatiosData> financialRatiosData = financialRatiosRepository.findBySymbol(ticker);

        if (financialRatiosData.isEmpty() || financialRatiosData.get().getAnnualReports().isEmpty() || financialRatiosData.get().getQuarterlyReports().isEmpty()) {
            LOGGER.warn("No financial ratios data found for ticker: {}", ticker);
            checklistSseService.sendSseEvent(sseEmitter, "ROIC calculation skipped: No data found.");
            return new ReportItem("reinvestmentCapacity", 0, "No annual or quarterly financial ratios data available.");
        }

        var averageRoic = calculate5yrAverageRoic(financialRatiosData);

        int score = 0;
        String explanation;
        if (averageRoic.compareTo(BigDecimal.valueOf(12)) < 0) { // ROIC < 12%
            score = 0;
            explanation = "ROIC is less than 12% (" + averageRoic.toPlainString() + "%), indicating the company might be destroying value.";
        } else if (averageRoic.compareTo(BigDecimal.valueOf(20)) < 0) { // ROIC 12% - 20%
            score = 3;
            explanation = "ROIC is between 12% and 20% (" + averageRoic.toPlainString() + "%), indicating the company is essentially breaking even on its capital costs.";
        } else { // ROIC > 20%
            score = 5;
            explanation = "ROIC is greater than 20% (" + averageRoic.toPlainString() + "%), indicating a strong competitive advantage.";
        }

        return new ReportItem("sustainedHighReturns", score, explanation);

    }

    private static BigDecimal calculate5yrAverageRoic(Optional<FinancialRatiosData> financialRatiosData) {
        List<BigDecimal> annualRoicValues = financialRatiosData.get().getAnnualReports().stream()
                .filter(report -> report.getRoic() != null)
                .sorted(Comparator.comparing(FinancialRatiosReport::getDate).reversed())
                .limit(5)
                .map(FinancialRatiosReport::getRoic)
                .map(roic -> enforcePercent(roic))
                .collect(Collectors.toList());

        // Calculate average
        if (annualRoicValues.isEmpty()) {
            // Handle case with no values, e.g., BigDecimal.ZERO or null
            return BigDecimal.ZERO;
        } else {
            BigDecimal sum = annualRoicValues.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return sum.divide(
                    BigDecimal.valueOf(annualRoicValues.size()),
                    10,  // scale: adjust as needed for precision (e.g., 4 for percentage)
                    BigDecimal.ROUND_HALF_UP  // rounding mode
            );
        }
    }

    private static BigDecimal calculate5yrAverageRoe(Optional<FinancialRatiosData> financialRatiosData) {
        List<BigDecimal> annualRoeValues = financialRatiosData.get().getAnnualReports().stream()
                .filter(report -> report.getReturnOnEquity() != null)
                .sorted(Comparator.comparing(FinancialRatiosReport::getDate).reversed())
                .limit(5)
                .map(FinancialRatiosReport::getReturnOnEquity)
                .map(roe -> enforcePercent(roe))
                .collect(Collectors.toList());

        // Calculate average
        if (annualRoeValues.isEmpty()) {
            // Handle case with no values, e.g., BigDecimal.ZERO or null
            return BigDecimal.ZERO;
        } else {
            BigDecimal sum = annualRoeValues.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return sum.divide(
                    BigDecimal.valueOf(annualRoeValues.size()),
                    10,  // scale: adjust as needed for precision (e.g., 4 for percentage)
                    BigDecimal.ROUND_HALF_UP  // rounding mode
            );
        }
    }

    private double calculateReinvestmentRate5y(String ticker, SseEmitter sseEmitter) {
        Optional<IncomeStatementData> incomeStatementDataOpt = incomeStatementRepository.findBySymbol(ticker);
        Optional<CashFlowData> cashFlowDataOpt = cashFlowRepository.findBySymbol(ticker);

        if (incomeStatementDataOpt.isEmpty() || cashFlowDataOpt.isEmpty()) {
            LOGGER.warn("Missing financial reports for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "Missing cash flow or income reports for " + ticker);
            return 0.0;
        }

        List<IncomeReport> incomeReports = new ArrayList<>(incomeStatementDataOpt.get().getAnnualReports());
        List<CashFlowReport> cashFlowReports = new ArrayList<>(cashFlowDataOpt.get().getAnnualReports());

        incomeReports.sort(Comparator.comparing((IncomeReport r) -> LocalDate.parse(r.getDate())).reversed());
        cashFlowReports.sort(Comparator.comparing((CashFlowReport r) -> LocalDate.parse(r.getDate())).reversed());

        Map<String, CashFlowReport> cashFlowReportMap = cashFlowReports.stream()
                .collect(Collectors.toMap(CashFlowReport::getFiscalYear, report -> report, (r1, r2) -> r1));

        List<IncomeReport> recentIncomeReports = incomeReports.stream().limit(5).toList();

        double totalReinvestedCapital = 0.0;
        double totalAdjustedEarnings = 0.0;

        for (IncomeReport incomeReport : recentIncomeReports) {
            String fiscalYear = incomeReport.getFiscalYear();
            CashFlowReport cashFlowReport = cashFlowReportMap.get(fiscalYear);

            if (cashFlowReport == null) continue;

            // 1. Get raw values
            double capex = Math.abs(safeParser.tryParseDouble(cashFlowReport.getCapitalExpenditure()));
            // Note: Depreciation is usually in Income Statement or added back in Cash Flow Operating section
            double depreciation = safeParser.tryParseDouble(incomeReport.getDepreciationAndAmortization());
            double rnd = safeParser.tryParseDouble(incomeReport.getResearchAndDevelopmentExpenses());
            double mna = Math.abs(safeParser.tryParseDouble(cashFlowReport.getAcquisitionsNet()));
            double netIncome = safeParser.tryParseDouble(incomeReport.getNetIncome());

            // 2. Calculate Net Investment
            // Logic: We spent CapEx, but some of that was just replacing old stuff (Depreciation).
            // Real growth investment is (CapEx - Depreciation).
            double netCapEx = capex - depreciation;

            // If Net CapEx is negative (Depreciation > CapEx), the company is shrinking its asset base,
            // but we usually allow this to offset R&D or M&A in the aggregate sum.

            totalReinvestedCapital += (netCapEx + rnd + mna);

            // 3. Calculate Adjusted Earnings Base
            // Logic: Since we treat R&D as an investment (in the numerator), we must add it back
            // to Net Income (because it was subtracted to get Net Income).
            totalAdjustedEarnings += (netIncome + rnd);
        }

        // Guard against division by zero or negative total earnings (unprofitable over 5y)
        if (totalAdjustedEarnings <= 0) {
            return 0.0; // Or handle as "Cannot reinvest profitably"
        }

        return totalReinvestedCapital / totalAdjustedEarnings;

    }

    private double calculateInvestedCapitalChange(String ticker, SseEmitter sseEmitter) {
        Optional<BalanceSheetData> balanceSheetDataOpt = balanceSheetRepository.findBySymbol(ticker);

        if (balanceSheetDataOpt.isEmpty()) {
            LOGGER.warn("Missing balance sheet reports for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "Missing balance sheet reports for " + ticker);
            return 0.0;
        }

        List<BalanceSheetReport> balanceSheetReports = new ArrayList<>(balanceSheetDataOpt.get().getAnnualReports());
        balanceSheetReports.sort(Comparator.comparing((BalanceSheetReport r) -> LocalDate.parse(r.getDate())).reversed());

        if (balanceSheetReports.size() < 2) {
            LOGGER.warn("Not enough historical data to calculate invested capital change for ticker: {}", ticker);
            return 0.0;
        }

        BalanceSheetReport latestReport = balanceSheetReports.get(0);
        BalanceSheetReport oldestReport;

        if (balanceSheetReports.size() >= 5) {
            oldestReport = balanceSheetReports.get(4);
        } else {
            oldestReport = balanceSheetReports.get(balanceSheetReports.size() - 1);
        }

        double investedCapitalLatest = calculateInvestedCapital(latestReport);
        double investedCapitalOldest = calculateInvestedCapital(oldestReport);

        if (investedCapitalOldest == 0) {
            return 0.0;
        }

        return (investedCapitalLatest - investedCapitalOldest) / investedCapitalOldest;
    }

    private String classifyInvestedCapitalTrend(double change) {
        if (change > 0.20) return "Increasing";
        if (change < -0.10) return "Declining";
        return "Flat";
    }

    private double calculateInvestedCapital(BalanceSheetReport report) {
        double totalDebt = safeParser.parse(report.getTotalDebt()).doubleValue();
        double totalStockholdersEquity = safeParser.parse(report.getTotalStockholdersEquity()).doubleValue();
        double cashAndCashEquivalents = safeParser.parse(report.getCashAndCashEquivalents()).doubleValue();

        return totalDebt + totalStockholdersEquity - cashAndCashEquivalents;
    }

    private static BigDecimal enforcePercent(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO;
        if (v.compareTo(BigDecimal.ONE) < 0) {
            return v.multiply(BigDecimal.valueOf(100));
        }
        return v;
    }
}
