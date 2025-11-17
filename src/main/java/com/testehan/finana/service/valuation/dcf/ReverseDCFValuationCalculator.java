package com.testehan.finana.service.valuation.dcf;

import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.ReverseDcfOutput;
import com.testehan.finana.model.valuation.dcf.ReverseDcfUserInput;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;

@Service
public class ReverseDCFValuationCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int MAX_ITERATIONS = 100;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000000001"); // 1e-9
    private static final BigDecimal LOW_GROWTH = new BigDecimal("-1.0");
    private static final BigDecimal HIGH_GROWTH = new BigDecimal("2.0");

    public ReverseDcfOutput calculateImpliedGrowthRate(DcfCalculationData data, ReverseDcfUserInput input) {
        // Extract inputs
        BigDecimal discountRate = BigDecimal.valueOf(input.getDiscountRate());
        BigDecimal perpetualGrowthRate = BigDecimal.valueOf(input.getPerpetualGrowthRate());
        int projectionYears = input.getProjectionYears();

        // Validate inputs
        if (discountRate.compareTo(perpetualGrowthRate) <= 0) {
            // WACC must be greater than perpetual growth rate for the model to work
            return ReverseDcfOutput.builder()
                .impliedFCFGrowthRate(null)
                .build();
        }

        // Extract data from records
        BigDecimal sharesOutstanding = data.meta().sharesOutstanding();
        BigDecimal currentSharePrice = data.meta().currentSharePrice();
        BigDecimal totalCashAndEquivalents = data.balanceSheet().totalCashAndEquivalents();
        BigDecimal totalShortTermDebt = data.balanceSheet().totalShortTermDebt();
        BigDecimal totalLongTermDebt = data.balanceSheet().totalLongTermDebt();
        BigDecimal operatingCashFlow = data.cashFlow().operatingCashFlow();
        BigDecimal capitalExpenditure = data.cashFlow().capitalExpenditure();

        // Calculate Enterprise Value from current market price
        // EV = (Share Price * Shares Outstanding) - Cash + Debt
        BigDecimal marketCap = currentSharePrice.multiply(sharesOutstanding, MC);
        BigDecimal totalDebt = totalShortTermDebt.add(totalLongTermDebt, MC);
        BigDecimal enterpriseValue = marketCap.subtract(totalCashAndEquivalents, MC).add(totalDebt, MC);

        // Calculate base FCF (Operating Cash Flow - |CapEx|)
        BigDecimal baseFcf = operatingCashFlow.subtract(capitalExpenditure.abs(), MC);

        // If base FCF is zero or negative, we can't calculate implied growth
        if (baseFcf.compareTo(BigDecimal.ZERO) <= 0) {
            return ReverseDcfOutput.builder()
                .impliedFCFGrowthRate(null)
                .build();
        }

        // Binary search to find implied growth rate
        BigDecimal low = LOW_GROWTH;
        BigDecimal high = HIGH_GROWTH;
        BigDecimal mid = BigDecimal.ZERO;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            mid = low.add(high, MC).divide(BigDecimal.valueOf(2), MC);

            // Calculate enterprise value with current growth rate guess
            BigDecimal calculatedEv = calculateEnterpriseValue(
                baseFcf, mid, discountRate, perpetualGrowthRate, projectionYears
            );

            // Calculate difference
            BigDecimal difference = calculatedEv.subtract(enterpriseValue, MC);

            // Check if we've converged
            if (difference.abs().compareTo(TOLERANCE) < 0) {
                break;
            }

            // Adjust bounds based on difference
            // If calculated EV > actual EV, growth rate is too high
            if (difference.compareTo(BigDecimal.ZERO) > 0) {
                high = mid;
            } else {
                low = mid;
            }
        }

        // 1. Get a realistic benchmark for the sector
        String sector = data.meta().sector();
        double benchmarkRate = 0.07; // Default market average
        if (sector != null) {
            benchmarkRate = switch (sector.toUpperCase()) {
                case "TECHNOLOGY", "SOFTWARE" -> 0.15; // 15%
                case "SEMICONDUCTORS"          -> 0.12; // 12%
                case "HEALTHCARE"              -> 0.08; // 8%
                case "CONSUMER STAPLES"        -> 0.04; // 4%
                case "UTILITIES"               -> 0.03; // 3%
                default                        -> 0.07; // 7% Market Average
            };
        }

        // 2. Calculate verdict
        String verdict = calculateVerdict(mid.doubleValue(), benchmarkRate);

        // Return the implied growth rate as a percentage (e.g., 0.05 for 5%)
        return ReverseDcfOutput.builder()
            .impliedFCFGrowthRate(mid.doubleValue())
            .verdict(verdict)
            .build();
    }

    /**
     * Calculates enterprise value given a growth rate assumption
     */
    private BigDecimal calculateEnterpriseValue(
            BigDecimal baseFcf,
            BigDecimal growthRate,
            BigDecimal discountRate,
            BigDecimal perpetualGrowthRate,
            int projectionYears) {

        BigDecimal presentValue = BigDecimal.ZERO;
        BigDecimal lastFcf = BigDecimal.ZERO;

        // Calculate present value of projected FCFs
        for (int year = 1; year <= projectionYears; year++) {
            // FCF for year = baseFcf * (1 + growthRate)^year
            BigDecimal fcf = baseFcf.multiply(
                BigDecimal.ONE.add(growthRate, MC).pow(year, MC),
                MC
            );

            // Discount factor = (1 + discountRate)^year
            BigDecimal discountFactor = BigDecimal.ONE.add(discountRate, MC).pow(year, MC);

            // Discounted FCF
            presentValue = presentValue.add(fcf.divide(discountFactor, MC), MC);

            if (year == projectionYears) {
                lastFcf = fcf;
            }
        }

        // Calculate terminal value using Gordon Growth Model
        // Terminal Value = (Last FCF * (1 + perpetualGrowthRate)) / (discountRate - perpetualGrowthRate)
        BigDecimal terminalValueNumerator = lastFcf.multiply(
            BigDecimal.ONE.add(perpetualGrowthRate, MC),
            MC
        );
        BigDecimal terminalValueDenominator = discountRate.subtract(perpetualGrowthRate, MC);
        BigDecimal terminalValue = terminalValueNumerator.divide(terminalValueDenominator, MC);

        // Discount terminal value to present
        BigDecimal terminalDiscountFactor = BigDecimal.ONE.add(discountRate, MC).pow(projectionYears, MC);
        BigDecimal discountedTerminalValue = terminalValue.divide(terminalDiscountFactor, MC);

        // Total enterprise value
        return presentValue.add(discountedTerminalValue, MC);
    }

    private String calculateVerdict(double impliedRate, double estimatedRate) {
        // 3% margin of safety expressed as a decimal
        double threshold = 0.03;

        // Example: 0.15 (estimated) - 0.05 (implied) = 0.10 spread
        double spread = estimatedRate - impliedRate;

        if (spread > threshold) {
            return "Undervalued";
        } else if (spread < -threshold) {
            return "Overvalued";
        } else {
            return "Neutral";
        }
    }
}
