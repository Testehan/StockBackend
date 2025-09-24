package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.FinancialRatiosData;
import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.model.IncomeStatementData;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.service.reporting.FerolSseService;
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
    private final FerolSseService ferolSseService;
    private final SafeParser safeParser;

    public FcfCalculator(FinancialRatiosRepository financialRatiosRepository, IncomeStatementRepository incomeStatementRepository, FerolSseService ferolSseService, SafeParser safeParser) {
        this.financialRatiosRepository = financialRatiosRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.ferolSseService = ferolSseService;
        this.safeParser = safeParser;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        ferolSseService.sendSseEvent(sseEmitter, "Calculating Free Cash Flow (FCF)...");

        Optional<FinancialRatiosData> financialRatiosDataOptional = financialRatiosRepository.findBySymbol(ticker);
        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);

        if (financialRatiosDataOptional.isEmpty() || financialRatiosDataOptional.get().getQuarterlyReports().isEmpty() ||
            incomeStatementDataOptional.isEmpty() || incomeStatementDataOptional.get().getQuarterlyReports().isEmpty()) {
            LOGGER.warn("No sufficient data for FCF calculation for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "FCF calculation skipped: Insufficient data found.");
            return new FerolReportItem("freeCashFlow", 0, "Insufficient data for Free Cash Flow calculation.");
        }

        List<FinancialRatiosReport> financialRatiosReports = financialRatiosDataOptional.get().getQuarterlyReports();
        List<IncomeReport> incomeReports = incomeStatementDataOptional.get().getQuarterlyReports();

        // Sort reports by fiscal date ending in descending order
        financialRatiosReports.sort(Comparator.comparing(FinancialRatiosReport::getDate).reversed());
        incomeReports.sort(Comparator.comparing(IncomeReport::getDate).reversed());

        // Helper to get income report for a specific date
        Map<String, IncomeReport> incomeReportMap = incomeReports.stream()
                .collect(Collectors.toMap(IncomeReport::getDate, report -> report, (r1, r2) -> r1)); // Handle potential duplicates


        // Calculate TTM FCFs (Current and Previous Year)
        List<BigDecimal> currentTtmAdjustedFcfs = new ArrayList<>();
        List<BigDecimal> previousTtmAdjustedFcfs = new ArrayList<>();
        StringBuilder currentTtmQuarterlyDetails = new StringBuilder();
        StringBuilder previousTtmQuarterlyDetails = new StringBuilder();

        for (int i = 0; i < 8; i++) { // Need up to 8 quarters for current and previous TTM
            if (i < financialRatiosReports.size()) {
                FinancialRatiosReport fr = financialRatiosReports.get(i);
                IncomeReport ir = incomeReportMap.get(fr.getDate());

                if (fr.getFreeCashFlow() != null && ir != null && ir.getOperatingIncome() != null && ir.getDepreciationAndAmortization() != null) {
                    BigDecimal operatingIncome = safeParser.parse(ir.getOperatingIncome());
                    BigDecimal depreciationAndAmortization = safeParser.parse(ir.getDepreciationAndAmortization());

                    // EBITDA = Operating Income + Depreciation & Amortization
                    BigDecimal quarterlyEbitda = operatingIncome.add(depreciationAndAmortization);
                    // Stock-Based Compensation = EBITDA - Operating Income (as per user's definition)
                    BigDecimal stockBasedCompensation = quarterlyEbitda.subtract(operatingIncome);
                    // Adjusted FCF = FCF - SBC
                    BigDecimal adjustedFcf = fr.getFreeCashFlow().subtract(stockBasedCompensation);

                    if (i < 4) { // Current TTM
                        currentTtmAdjustedFcfs.add(adjustedFcf);
                        currentTtmQuarterlyDetails.append("Q").append(4 - i).append(": ").append(adjustedFcf.toPlainString()).append(" (FCF: ").append(fr.getFreeCashFlow().toPlainString()).append(", SBC: ").append(stockBasedCompensation.toPlainString()).append("); ");
                    } else { // Previous TTM
                        previousTtmAdjustedFcfs.add(adjustedFcf);
                        previousTtmQuarterlyDetails.append("Q").append(8 - i).append(": ").append(adjustedFcf.toPlainString()).append(" (FCF: ").append(fr.getFreeCashFlow().toPlainString()).append(", SBC: ").append(stockBasedCompensation.toPlainString()).append("); ");
                    }
                }
            }
        }

        if (currentTtmAdjustedFcfs.size() < 4) {
            ferolSseService.sendSseEvent(sseEmitter, "FCF calculation partially skipped: Less than 4 quarters of data for current TTM FCF.");
            return new FerolReportItem("freeCashFlow", 0, "Less than 4 quarters of data for current TTM Free Cash Flow.");
        }

        BigDecimal currentTtmFcf = currentTtmAdjustedFcfs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        ferolSseService.sendSseEvent(sseEmitter, "Current TTM Adjusted FCF: " + currentTtmFcf.toPlainString());

        int score;
        String explanation;

        // Build a detailed explanation string with numbers
        StringBuilder detailedExplanation = new StringBuilder();
        detailedExplanation.append("Current TTM Adjusted FCF: ").append(currentTtmFcf.toPlainString()).append(" (Quarterly breakdown: ").append(currentTtmQuarterlyDetails.toString()).append("). ");


        if (currentTtmFcf.compareTo(BigDecimal.ZERO) < 0) { // Negative FCF
            score = 0;
            explanation = "FCF is Negative, indicating the company is a 'Cash Burner'.";
        } else {
            // FCF is positive, now check growth
            if (previousTtmAdjustedFcfs.size() < 4) {
                // Not enough data for YoY growth, assume "Survivor" if positive but no growth data
                score = 1;
                explanation = "FCF is Positive, but insufficient data (less than 4 quarters for previous year) to assess growth, categorizing as 'Survivor'.";
            } else {
                BigDecimal previousTtmFcf = previousTtmAdjustedFcfs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                detailedExplanation.append("Previous TTM Adjusted FCF: ").append(previousTtmFcf.toPlainString()).append(" (Quarterly breakdown: ").append(previousTtmQuarterlyDetails.toString()).append("). ");
                ferolSseService.sendSseEvent(sseEmitter, "Previous TTM Adjusted FCF: " + previousTtmFcf.toPlainString());

                if (previousTtmFcf.compareTo(BigDecimal.ZERO) <= 0) { // Avoid division by zero or negative growth from zero/negative
                     if (currentTtmFcf.compareTo(BigDecimal.ZERO) > 0) {
                         score = 1; // Positive FCF but previous was zero or negative, cannot calculate meaningful growth percentage directly
                         explanation = "FCF is Positive, but previous FCF was zero or negative, categorizing as 'Survivor'.";
                     } else { // Should not happen given outer if, but for completeness
                         score = 0;
                         explanation = "FCF is Negative, indicating a 'Cash Burner'.";
                     }
                } else {
                    BigDecimal growthPercentage = currentTtmFcf.subtract(previousTtmFcf)
                                                                .divide(previousTtmFcf, 4, BigDecimal.ROUND_HALF_UP)
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
        return new FerolReportItem("freeCashFlow", score, detailedExplanation.toString() + explanation);
    }
}
