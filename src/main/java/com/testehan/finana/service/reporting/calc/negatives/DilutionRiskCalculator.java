package com.testehan.finana.service.reporting.calc.negatives;

import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.service.reporting.FerolSseService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Objects;

@Service
public class DilutionRiskCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DilutionRiskCalculator.class);

    private static final BigDecimal FIVE_PERCENT = new BigDecimal("5.0");
    private static final BigDecimal THREE_PERCENT = new BigDecimal("3.0");

    private final IncomeStatementRepository incomeStatementRepository;
    private final FerolSseService ferolSseService;

    public DilutionRiskCalculator(IncomeStatementRepository incomeStatementRepository, FerolSseService ferolSseService) {
        this.incomeStatementRepository = incomeStatementRepository;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        var incomeDataOptional = incomeStatementRepository.findBySymbol(ticker);
        if (incomeDataOptional.isPresent() && !Objects.isNull(incomeDataOptional.get().getAnnualReports())
            && ! incomeDataOptional.get().getAnnualReports().isEmpty()){

            var statements = incomeDataOptional.get().getAnnualReports();

            // Validation: Need at least 2 years to calculate growth
            if (statements == null || statements.size() < 2) {
                return getErrorFerolReportItem(ticker, sseEmitter, " Insufficient data to calculate growth for {}");
            }

            // Step 1: Sort by date (Oldest first) to ensure correct calculation
            statements.sort(Comparator.comparing(IncomeReport::getDate));

            // Step 2: Get Start and End values
            // We look at the oldest available and the newest available
            BigDecimal startShares = new BigDecimal(statements.get(0).getWeightedAverageShsOutDil());
            BigDecimal endShares = new BigDecimal(statements.get(statements.size() - 1).getWeightedAverageShsOutDil());

            // Step 3 : The number of growth periods is (DataPoints - 1)
            // e.g., 5 years of data = 4 years of growth transitions
            double periods = statements.size() - 1.0;

            int score;

            // Edge case: Avoid division by zero
            if (startShares.equals(BigDecimal.ZERO)) {
                score = -4; // Infinite dilution technically
            }

            // Step 4: Calculate CAGR
            // Formula: (End / Start) ^ (1/n) - 1

            // 4a. Calculate the Ratio (End/Start) with high precision (DECIMAL128)
            BigDecimal ratio = endShares.divide(startShares, MathContext.DECIMAL128);

            // 4b. Exponentiation
            // Note: BigDecimal does not support fractional exponents natively in Java 8-21
            // without external libraries
            // We convert to double for the `pow` operation, which is standard for this specific metric.
            double cagrRaw = Math.pow(ratio.doubleValue(), 1.0 / periods) - 1.0;

            // 4c. Convert back to BigDecimal for percentage formatting and comparison
            BigDecimal growthPercent = BigDecimal.valueOf(cagrRaw * 100)
                    .setScale(2, RoundingMode.HALF_UP); // Round to 2 decimal places

            String scoreExplanation = String.format("Calculated Annual Share Growth (CAGR) over the past %f years is : %.2f%%",periods, growthPercent);

            if (growthPercent.compareTo(FIVE_PERCENT) > 0) {
                score = -4; // > 5%
            } else if (growthPercent.compareTo(THREE_PERCENT) >= 0) {
                score = -2; // 3% to 5%
            } else {
                score = 0;  // < 3% (includes negative growth/buybacks)
            }

            return new FerolReportItem("extremeDilution", score, scoreExplanation);
        } else {
            return getErrorFerolReportItem(ticker, sseEmitter, " No income statement data found for {}");
        }

    }

    @NotNull
    private FerolReportItem getErrorFerolReportItem(String ticker, SseEmitter sseEmitter, String logMessage) {
        String errorMessage = "Operation 'calculateDilution' failed.";
        LOGGER.error(errorMessage + logMessage, ticker);
        ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
        return new FerolReportItem("extremeDilution", -10, "Something went wrong and score could not be calculated ");
    }
}
