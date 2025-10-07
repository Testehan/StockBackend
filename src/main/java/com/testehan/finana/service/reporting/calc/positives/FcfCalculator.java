package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.FinancialRatiosData;
import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.model.IncomeStatementData;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.service.reporting.ChecklistSseService;
import com.testehan.finana.util.SafeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FcfCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(FcfCalculator.class);

    private final FinancialRatiosRepository financialRatiosRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final ChecklistSseService ferolSseService;
    private final SafeParser safeParser;

    public FcfCalculator(FinancialRatiosRepository financialRatiosRepository, IncomeStatementRepository incomeStatementRepository, ChecklistSseService ferolSseService, SafeParser safeParser) {
        this.financialRatiosRepository = financialRatiosRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.ferolSseService = ferolSseService;
        this.safeParser = safeParser;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        ferolSseService.sendSseEvent(sseEmitter, "Calculating Free Cash Flow (FCF)...");

        Optional<FinancialRatiosData> financialRatiosDataOptional = financialRatiosRepository.findBySymbol(ticker);
        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);

        if (financialRatiosDataOptional.isEmpty() || financialRatiosDataOptional.get().getAnnualReports().isEmpty() ||
            incomeStatementDataOptional.isEmpty() || incomeStatementDataOptional.get().getAnnualReports().isEmpty()) {
            LOGGER.warn("No sufficient annual data for FCF calculation for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "FCF calculation skipped: Insufficient annual data found.");
            return new ReportItem("freeCashFlow", 0, "Insufficient annual data for Free Cash Flow calculation.");
        }

        List<FinancialRatiosReport> annualFinancialRatiosReports = financialRatiosDataOptional.get().getAnnualReports();
        List<IncomeReport> annualIncomeReports = incomeStatementDataOptional.get().getAnnualReports();

        // Sort reports by fiscal date ending in descending order
        annualFinancialRatiosReports.sort(Comparator.comparing(FinancialRatiosReport::getDate).reversed());
        annualIncomeReports.sort(Comparator.comparing(IncomeReport::getDate).reversed());

        if (annualFinancialRatiosReports.size() < 2 || annualIncomeReports.size() < 2) {
            LOGGER.warn("Less than two years of annual data for FCF calculation for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "FCF calculation skipped: Less than two years of annual data found.");
            return new ReportItem("freeCashFlow", 0, "Less than two years of annual data for Free Cash Flow calculation.");
        }

        FinancialRatiosReport currentYearFinancialRatiosReport = annualFinancialRatiosReports.get(0);
        IncomeReport currentYearIncomeReport = annualIncomeReports.get(0);

        FinancialRatiosReport previousYearFinancialRatiosReport = annualFinancialRatiosReports.get(1);
        IncomeReport previousYearIncomeReport = annualIncomeReports.get(1);

        // Helper to get income report for a specific date
        Map<String, IncomeReport> incomeReportMap = annualIncomeReports.stream()
                .collect(Collectors.toMap(IncomeReport::getDate, report -> report, (r1, r2) -> r1)); // Handle potential duplicates


        // Calculate Current Year Adjusted FCF
        BigDecimal currentYearAdjustedFcf = BigDecimal.ZERO;
        BigDecimal previousYearAdjustedFcf = BigDecimal.ZERO;

        if (currentYearFinancialRatiosReport.getFreeCashFlow() != null && currentYearIncomeReport.getOperatingIncome() != null && currentYearIncomeReport.getDepreciationAndAmortization() != null) {
            BigDecimal operatingIncome = safeParser.parse(currentYearIncomeReport.getOperatingIncome());
            BigDecimal depreciationAndAmortization = safeParser.parse(currentYearIncomeReport.getDepreciationAndAmortization());

            // EBITDA = Operating Income + Depreciation & Amortization
            BigDecimal annualEbitda = operatingIncome.add(depreciationAndAmortization);
            // Stock-Based Compensation = EBITDA - Operating Income (as per user's definition)
            BigDecimal stockBasedCompensation = annualEbitda.subtract(operatingIncome);
            // Adjusted FCF = FCF - SBC
            currentYearAdjustedFcf = currentYearFinancialRatiosReport.getFreeCashFlow().subtract(stockBasedCompensation);
        }

        // Calculate Previous Year Adjusted FCF
        if (previousYearFinancialRatiosReport.getFreeCashFlow() != null && previousYearIncomeReport.getOperatingIncome() != null && previousYearIncomeReport.getDepreciationAndAmortization() != null) {
            BigDecimal operatingIncome = safeParser.parse(previousYearIncomeReport.getOperatingIncome());
            BigDecimal depreciationAndAmortization = safeParser.parse(previousYearIncomeReport.getDepreciationAndAmortization());

            // EBITDA = Operating Income + Depreciation & Amortization
            BigDecimal annualEbitda = operatingIncome.add(depreciationAndAmortization);
            // Stock-Based Compensation = EBITDA - Operating Income (as per user's definition)
            BigDecimal stockBasedCompensation = annualEbitda.subtract(operatingIncome);
            // Adjusted FCF = FCF - SBC
            previousYearAdjustedFcf = previousYearFinancialRatiosReport.getFreeCashFlow().subtract(stockBasedCompensation);
        }

        if (currentYearAdjustedFcf.compareTo(BigDecimal.ZERO) == 0) {
            ferolSseService.sendSseEvent(sseEmitter, "FCF calculation partially skipped: Current year adjusted FCF is zero.");
            return new ReportItem("freeCashFlow", 0, "Current year adjusted FCF is zero. Cannot assess growth.");
        }


        ferolSseService.sendSseEvent(sseEmitter, "Current Year Adjusted FCF: " + currentYearAdjustedFcf.toPlainString());

        int score;
        String explanation;

        // Build a detailed explanation string with numbers
        StringBuilder detailedExplanation = new StringBuilder();
        detailedExplanation.append("Current Year Adjusted FCF: ").append(currentYearAdjustedFcf.toPlainString()).append(". ");


        if (currentYearAdjustedFcf.compareTo(BigDecimal.ZERO) < 0) { // Negative FCF
            score = 0;
            explanation = "FCF is Negative, indicating the company is a 'Cash Burner'.";
        } else {
            // FCF is positive, now check growth
            if (previousYearAdjustedFcf.compareTo(BigDecimal.ZERO) == 0) {
                // Not enough data for YoY growth, assume "Survivor" if positive but no growth data
                score = 1;
                explanation = "FCF is Positive, but previous FCF was zero, categorizing as 'Survivor'.";
            } else {
                ferolSseService.sendSseEvent(sseEmitter, "Previous Year Adjusted FCF: " + previousYearAdjustedFcf.toPlainString());
                detailedExplanation.append("Previous Year Adjusted FCF: ").append(previousYearAdjustedFcf.toPlainString()).append(". ");


                if (previousYearAdjustedFcf.compareTo(BigDecimal.ZERO) < 0) { // Avoid division by zero or negative growth from zero/negative
                     if (currentYearAdjustedFcf.compareTo(BigDecimal.ZERO) > 0) {
                         score = 1; // Positive FCF but previous was zero or negative, cannot calculate meaningful growth percentage directly
                         explanation = "FCF is Positive, but previous FCF was negative, categorizing as 'Survivor'.";
                     } else { // Should not happen given outer if, but for completeness
                         score = 0;
                         explanation = "FCF is Negative, indicating a 'Cash Burner'.";
                     }
                } else {
                    BigDecimal growthPercentage = currentYearAdjustedFcf.subtract(previousYearAdjustedFcf)
                                                                .divide(previousYearAdjustedFcf, 4, BigDecimal.ROUND_HALF_UP)
                                                                .multiply(BigDecimal.valueOf(100));

                    detailedExplanation.append("YoY Growth: ").append(growthPercentage.toPlainString()).append("%. ");
                    ferolSseService.sendSseEvent(sseEmitter, "FCF Growth (YoY): " + growthPercentage.toPlainString() + "%");

                    if (growthPercentage.compareTo(BigDecimal.valueOf(5)) < 0) { // 0% - 5% growth
                        score = 1;
                        explanation = "FCF is Positive with negligible growth, categorizing as 'Survivor'.";
                    } else if (growthPercentage.compareTo(BigDecimal.valueOf(15)) < 0) { // 5% - 15% growth
                        score = 2;
                        explanation = "FCF is Positive and growing steadily, categorizing as 'Compounder'.";
                    } else { // > 15% growth
                        score = 3;
                        explanation = "FCF is Positive and growing fast, categorizing as 'Cash Cow'.";
                    }
                }
            }
        }
        ferolSseService.sendSseEvent(sseEmitter, "FCF calculation complete. Score: " + score);
        return new ReportItem("freeCashFlow", score, detailedExplanation.toString() + explanation);
    }
}
