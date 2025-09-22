package com.testehan.finana.service.reporting.calc;

import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.service.reporting.FerolSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AcquisitionsCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcquisitionsCalculator.class);

    private final IncomeStatementRepository incomeStatementRepository;
    private final FerolSseService ferolSseService;

    public AcquisitionsCalculator(IncomeStatementRepository incomeStatementRepository, FerolSseService ferolSseService) {
        this.incomeStatementRepository = incomeStatementRepository;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        var incomeDataOptional = incomeStatementRepository.findBySymbol(ticker);
        if (incomeDataOptional.isPresent()){
            var incomeData = incomeDataOptional.get();

            List<IncomeReport> reports = incomeData.getAnnualReports();

            if (reports == null || reports.isEmpty()) {
                String errorMessage = "No income annual reports found for symbol " + ticker;
                LOGGER.error(errorMessage);
                ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            }

            // 1. Sort reports by date (Descending) to ensure we get the most recent ones first
            List<IncomeReport> sortedReports = reports.stream()
                    .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                    .limit(3) // We only care about the last 3 years
                    .collect(Collectors.toList());

            BigDecimal totalWeightedPercentage = BigDecimal.ZERO;
            int totalWeight = 0;

            // Weights: Most recent = 3, Next = 2, Last = 1
            int currentWeight = 3;

            for (IncomeReport report : sortedReports) {
                BigDecimal salesAndMarketExpense = new BigDecimal(report.getSellingAndMarketingExpenses());
                BigDecimal grossProfit = new BigDecimal(report.getGrossProfit());

                // 2. Fallback: If S&M is null or Zero, use SG&A
                if (salesAndMarketExpense == null || salesAndMarketExpense.compareTo(BigDecimal.ZERO) == 0) {
                    salesAndMarketExpense =  new BigDecimal(report.getSellingGeneralAndAdministrativeExpenses());
                    // Optional: You could log that you are using a proxy here
                }

                // Safety check: Skip if data is missing or Gross Profit is 0/Negative to avoid math errors
                if (salesAndMarketExpense == null || grossProfit == null || grossProfit.compareTo(BigDecimal.ZERO) <= 0) {
                    currentWeight--;
                    continue;
                }

                // Calculate ratio for this year: (S&M / GP) * 100
                BigDecimal yearRatio = salesAndMarketExpense.divide(grossProfit, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

                // Apply Weight
                totalWeightedPercentage = totalWeightedPercentage.add(yearRatio.multiply(new BigDecimal(currentWeight)));
                totalWeight += currentWeight;

                currentWeight--;
            }

            if (totalWeight == 0) {
                String errorMessage = "Data invalid for calculation of s&m % out of gross profit ";
                LOGGER.error(errorMessage);
                ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            }

            // Final Calculation
            BigDecimal finalAverage = totalWeightedPercentage.divide(new BigDecimal(totalWeight), 2, RoundingMode.HALF_UP);

            // Calculate Score (0-5)
            int score = calculateAquisitionScore(finalAverage);

            return new FerolReportItem("customerAcquisition", score, "The average weighted (last year has highest weight) S&M % of Gross Profit for last 3 years is " + finalAverage.toPlainString() + "%");

        } else {
            String errorMessage = "Operation 'calculateAcquisitions' failed.";
            LOGGER.error(errorMessage + " No income data found for {}",ticker);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
        }

        return new FerolReportItem("customerAcquisition", -10, "Something went wrong and score could not be calculated ");
    }

    private int calculateAquisitionScore(BigDecimal percentage) {
        double val = percentage.doubleValue();

        if (val < 10) return 5;       // Word of Mouth / Product Led Growth
        if (val < 20) return 4;       // Very Efficient
        if (val < 40) return 3;       // Normal / Benchmark
        if (val < 60) return 2;       // Getting Expensive
        if (val < 80) return 1;       // Expensive
        return 0;                     // Very Expensive / Burning Cash
    }
}
