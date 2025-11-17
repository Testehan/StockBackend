package com.testehan.finana.service.valuation.growth;

import com.testehan.finana.model.valuation.growth.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.List;

@Service
public class GrowthValuationCalculator {

    private static final MathContext MC = MathContext.DECIMAL64; // Using DECIMAL64 for 16 digits of precision

    public GrowthOutput calculateIntrinsicValue(GrowthValuationData data, GrowthUserInput input) {
        // Initialize variables based on input and data
        BigDecimal initialRevenueGrowthRate = input.getInitialRevenueGrowthRate().divide(BigDecimal.valueOf(100), MC);
        int growthFadePeriod = input.getGrowthFadePeriod();
        BigDecimal terminalGrowthRate = input.getTerminalGrowthRate().divide(BigDecimal.valueOf(100), MC);
        int yearsToProject = input.getYearsToProject();
        BigDecimal targetOperatingMargin = input.getTargetOperatingMargin().divide(BigDecimal.valueOf(100), MC);
        int yearsToReachTargetMargin = input.getYearsToReachTargetMargin();
        BigDecimal marginalTaxRate = input.getMarginalTaxRate().divide(BigDecimal.valueOf(100), MC);
        BigDecimal initialCostOfCapital = input.getInitialCostOfCapital().divide(BigDecimal.valueOf(100), MC);
        BigDecimal terminalCostOfCapital = input.getTerminalCostOfCapital().divide(BigDecimal.valueOf(100), MC);
        int yearsOfRiskConvergence = input.getYearsOfRiskConvergence();
        BigDecimal probabilityOfFailure = input.getProbabilityOfFailure().divide(BigDecimal.valueOf(100), MC);
        BigDecimal distressProceeds = input.getDistressProceedsPctOfBookOrRevenue().divide(BigDecimal.valueOf(100), MC); // This is a percentage
        BigDecimal totalDebt = data.getTotalDebt();
        BigDecimal cashBalance = data.getCashBalance();
        BigDecimal commonSharesOutstanding = data.getCommonSharesOutstanding();

        // Get current data (latest year)
        // Ensure data is sorted
        List<IncomeStatementYear> sortedIncomeStatements = data.getIncomeStatements().stream()
                .sorted(Comparator.comparing(IncomeStatementYear::getFiscalYear))
                .toList();
        List<BalanceSheetYear> sortedBalanceSheets = data.getBalanceSheets().stream()
                .sorted(Comparator.comparing(BalanceSheetYear::getFiscalYear))
                .toList();

        if (sortedIncomeStatements.isEmpty() || sortedBalanceSheets.isEmpty()) {
            // Handle error: insufficient data
            throw new IllegalArgumentException("Insufficient financial statement data for growth valuation.");
        }

        IncomeStatementYear latestStatement = sortedIncomeStatements.get(sortedIncomeStatements.size() - 1);
        BigDecimal currentRevenue = latestStatement.getRevenue();
        BigDecimal currentOperatingIncome = latestStatement.getOperatingIncome();
        BigDecimal currentMargin = (currentRevenue.compareTo(BigDecimal.ZERO) != 0) ? currentOperatingIncome.divide(currentRevenue, MC) : BigDecimal.ZERO;
        // Cap the starting loss at -100% to prevent the "scaled loss" explosion
        currentMargin = currentMargin.max(BigDecimal.valueOf(-1.0));

        // Call the main calculation method
        BigDecimal pricePerShare = calculateEquityValue(
            currentRevenue,
            currentMargin,
            yearsToProject,
            initialRevenueGrowthRate,
            growthFadePeriod,
            terminalGrowthRate,
            targetOperatingMargin,
            yearsToReachTargetMargin,
            marginalTaxRate,
            // Reinvestment rate: The JS code uses reinvestmentRate but in GrowthUserInput it is reinvestmentAsPctOfRevenue
            // So, in JS: const reinvestment = incrementalRevenue / reinvestmentRate;
            // This means reinvestmentRate was a multiplier, not a percentage. If reinvestmentAsPctOfRevenue is 0.20 for 20%,
            // then reinvestment = incrementalRevenue / 0.20 = incrementalRevenue * 5. This was a bug in JS.
            // I will assume reinvestmentAsPctOfRevenue is the actual percentage to be applied to incremental revenue.
            input.getReinvestmentAsPctOfRevenue().divide(BigDecimal.valueOf(100), MC), // Corrected assumption: this is the percentage of incremental revenue reinvested
            initialCostOfCapital,
            terminalCostOfCapital,
            yearsOfRiskConvergence,
            probabilityOfFailure,
            distressProceeds,
            totalDebt,
            cashBalance,
            commonSharesOutstanding,
            sortedBalanceSheets
        );

        GrowthOutput output = new GrowthOutput();
        output.setIntrinsicValuePerShare(pricePerShare);
        
        // Determine verdict based on comparison with current share price
        BigDecimal currentSharePrice = data.getCurrentSharePrice();
        if (currentSharePrice == null || currentSharePrice.compareTo(BigDecimal.ZERO) <= 0) {
            output.setVerdict("Neutral");
        } else {
            BigDecimal margin = pricePerShare.subtract(currentSharePrice, MC)
                .divide(currentSharePrice, MC);
            double marginPercent = margin.doubleValue();
            
            if (marginPercent > 0.20) {
                output.setVerdict("Undervalued");
            } else if (marginPercent < -0.20) {
                output.setVerdict("Overvalued");
            } else {
                output.setVerdict("Neutral");
            }
        }
        
        return output;
    }

    public BigDecimal projectRevenue(BigDecimal currentRevenue, int year, BigDecimal initialRevenueGrowthRate, int nearTermYears, int growthFadePeriod, BigDecimal terminalGrowthRate) {
        BigDecimal growthRate;
        if (year <= nearTermYears) {
            growthRate = initialRevenueGrowthRate;
        } else if (year <= nearTermYears + growthFadePeriod) {
            BigDecimal fadeYears = BigDecimal.valueOf(year - nearTermYears);
            growthRate = initialRevenueGrowthRate.subtract(
                initialRevenueGrowthRate.subtract(terminalGrowthRate)
                    .divide(BigDecimal.valueOf(growthFadePeriod), MC)
                    .multiply(fadeYears, MC),
                MC
            );
        } else {
            growthRate = terminalGrowthRate;
        }
        return currentRevenue.multiply(BigDecimal.ONE.add(growthRate, MC), MC);
    }

    public BigDecimal projectOperatingMargin(BigDecimal currentMargin, int year, int yearsToReachTargetMargin, BigDecimal targetOperatingMargin) {
        if (yearsToReachTargetMargin <= 0) return targetOperatingMargin; // Avoid division by zero
        if (year >= yearsToReachTargetMargin) return targetOperatingMargin;
        return currentMargin.add(
            targetOperatingMargin.subtract(currentMargin, MC)
                .divide(BigDecimal.valueOf(yearsToReachTargetMargin), MC)
                .multiply(BigDecimal.valueOf(year), MC),
            MC
        );
    }

    public BigDecimal computeFCF(BigDecimal revenue, BigDecimal incrementalRevenue, BigDecimal operatingMargin, BigDecimal marginalTaxRate, BigDecimal reinvestmentAsPctOfRevenue) {
        BigDecimal operatingIncome = revenue.multiply(operatingMargin, MC);
        BigDecimal taxes = operatingIncome.compareTo(BigDecimal.ZERO) > 0 ? operatingIncome.multiply(marginalTaxRate, MC) : BigDecimal.ZERO; // Taxes can't be negative
        BigDecimal reinvestment = incrementalRevenue.multiply(reinvestmentAsPctOfRevenue, MC);
        return (operatingIncome.subtract(taxes, MC)).subtract(reinvestment, MC);
    }

    public BigDecimal discountRate(int year, int yearsOfRiskConvergence, BigDecimal initialCostOfCapital, BigDecimal terminalCostOfCapital) {
        if (yearsOfRiskConvergence <= 0) return terminalCostOfCapital; // Avoid division by zero
        if (year >= yearsOfRiskConvergence) return terminalCostOfCapital;
        return initialCostOfCapital.subtract(
            initialCostOfCapital.subtract(terminalCostOfCapital, MC)
                .divide(BigDecimal.valueOf(yearsOfRiskConvergence), MC)
                .multiply(BigDecimal.valueOf(year), MC),
            MC
        );
    }

    public BigDecimal terminalValue(BigDecimal finalFCF, BigDecimal terminalGrowthRate, BigDecimal terminalCostOfCapital) {
        // Gordon Growth Model: TV = FCF × (1 + g) / (WACC - g)
        BigDecimal denominator = terminalCostOfCapital.subtract(terminalGrowthRate, MC);
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            // Handle case where denominator is zero or negative (e.g., if growth rate > discount rate)
            // This would imply infinite value or negative value, which is not realistic in this model.
            // Return a very large number or throw an exception, depending on desired behavior.
            // For now, return 0.0 to avoid unexpected results.
            return BigDecimal.ZERO;
        }
        return finalFCF.multiply(BigDecimal.ONE.add(terminalGrowthRate, MC), MC).divide(denominator, MC);
    }

    public BigDecimal calculateEquityValue(
        BigDecimal currentRevenue,
        BigDecimal currentMargin,
        int yearsToProject,
        BigDecimal initialRevenueGrowthRate,
        int growthFadePeriod,
        BigDecimal terminalGrowthRate,
        BigDecimal targetOperatingMargin,
        int yearsToReachTargetMargin,
        BigDecimal marginalTaxRate,
        BigDecimal reinvestmentAsPctOfRevenue,
        BigDecimal initialCostOfCapital,
        BigDecimal terminalCostOfCapital,
        int yearsOfRiskConvergence,
        BigDecimal probabilityOfFailure,
        BigDecimal distressProceeds, // This is a percentage of current revenue or book value, not a rate
        BigDecimal totalDebt,
        BigDecimal cashBalance,
        BigDecimal commonSharesOutstanding,
        List<BalanceSheetYear> sortedBalanceSheets
    ) {
        BigDecimal revenue = currentRevenue;
        BigDecimal previousRevenue = currentRevenue;
        BigDecimal equityValue = BigDecimal.ZERO;
        BigDecimal fcf = BigDecimal.ZERO;
        BigDecimal cumulativeDiscountFactor = BigDecimal.ONE;
        int nearTermYears = 3; // From the JS code
        BigDecimal terminalVal = BigDecimal.ZERO; // Declare terminalVal here


        for (int year = 1; year <= yearsToProject; year++) {
            revenue = projectRevenue(revenue, year, initialRevenueGrowthRate, nearTermYears, growthFadePeriod, terminalGrowthRate);
            BigDecimal incrementalRevenue = revenue.subtract(previousRevenue, MC);
            BigDecimal margin = projectOperatingMargin(currentMargin, year, yearsToReachTargetMargin, targetOperatingMargin);
            fcf = computeFCF(revenue, incrementalRevenue, margin, marginalTaxRate, reinvestmentAsPctOfRevenue);

            // Cumulative discounting - multiply by this year's discount factor
            BigDecimal yearDiscountRate = discountRate(year, yearsOfRiskConvergence, initialCostOfCapital, terminalCostOfCapital);
            cumulativeDiscountFactor = cumulativeDiscountFactor.multiply(BigDecimal.ONE.add(yearDiscountRate, MC), MC);

            // Discount FCF using cumulative discount factor
            equityValue = equityValue.add(fcf.divide(cumulativeDiscountFactor, MC), MC);

            previousRevenue = revenue;
        }

        // Smarter Terminal Value Calculation
        BigDecimal perpetuityGrowth = terminalGrowthRate;
        BigDecimal terminalWACC = terminalCostOfCapital;

        // Ensure denominator is not zero or negative for terminal value
        BigDecimal terminalValueDenominator = terminalWACC.subtract(perpetuityGrowth, MC);
        if (terminalValueDenominator.compareTo(BigDecimal.ZERO) <= 0) {
            // Handle case where denominator is zero or negative (e.g., if growth rate >= discount rate)
            // This would imply infinite value or negative value, which is not realistic in this model.
            // In such cases, a more conservative approach might be needed, e.g., liquidation value or 0.
            // For now, return 0 for terminal value to avoid unexpected results.
            terminalVal = BigDecimal.ZERO;
        } else {
            // Sustainable reinvestment in terminal year:
            // Reinvestment = (Growth / Return on Capital) * After-tax Operating Income
            // For simplicity, assume reinvestment = (perpetuityGrowth / terminalWACC) * OperatingIncome
            // Note: The formula provided in the prompt `(perpetuityGrowth / terminalWACC)` for reinvestment
            // implies that Return on Capital is assumed to be terminalWACC.
            // Also, after-tax operating income needs to be calculated first.

            BigDecimal terminalOperatingIncome = revenue.multiply(targetOperatingMargin, MC);
            BigDecimal terminalTaxes = terminalOperatingIncome.compareTo(BigDecimal.ZERO) > 0 ? terminalOperatingIncome.multiply(marginalTaxRate, MC) : BigDecimal.ZERO;
            BigDecimal afterTaxOperatingIncome = terminalOperatingIncome.subtract(terminalTaxes, MC);

            BigDecimal terminalReinvestmentRate = BigDecimal.ZERO;
            if (terminalWACC.compareTo(BigDecimal.ZERO) > 0) {
                 terminalReinvestmentRate = perpetuityGrowth.divide(terminalWACC, MC);
            }
            // Ensure reinvestment rate is not excessively high
            if (terminalReinvestmentRate.compareTo(BigDecimal.ONE) > 0) {
                 terminalReinvestmentRate = BigDecimal.ONE;
            }


            BigDecimal terminalReinvestment = afterTaxOperatingIncome.multiply(terminalReinvestmentRate, MC);
            BigDecimal terminalFCF = afterTaxOperatingIncome.subtract(terminalReinvestment, MC);


            terminalVal = terminalFCF.divide(terminalValueDenominator, MC);
        }
        // Discount terminal value using cumulative discount factor
        equityValue = equityValue.add(terminalVal.divide(cumulativeDiscountFactor, MC), MC);

        // Calculate distress value as percentage of current revenue
        BigDecimal distressValue = currentRevenue.multiply(distressProceeds, MC); // distressProceeds is already a percentage

        // Adjust for failure probability
        equityValue = (BigDecimal.ONE.subtract(probabilityOfFailure, MC)).multiply(equityValue, MC).add(probabilityOfFailure.multiply(distressValue, MC), MC);

        // Subtract debt, add cash
        if (commonSharesOutstanding.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // Avoid division by zero
        }
        return (equityValue.subtract(totalDebt, MC).add(cashBalance, MC)).divide(commonSharesOutstanding, MC);
    }
}
