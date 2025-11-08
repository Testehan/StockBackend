package com.testehan.finana.service.valuation.growth;

import com.testehan.finana.model.valuation.growth.*;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class GrowthValuationCalculator {

    public GrowthOutput calculateIntrinsicValue(GrowthValuationData data, GrowthUserInput input) {
        // Initialize variables based on input and data
        double initialRevenueGrowthRate = input.getInitialRevenueGrowthRate() / 100.0;
        int growthFadePeriod = input.getGrowthFadePeriod();
        double terminalGrowthRate = input.getTerminalGrowthRate() / 100.0;
        int yearsToProject = input.getYearsToProject();
        double targetOperatingMargin = input.getTargetOperatingMargin() / 100.0;
        int yearsToReachTargetMargin = input.getYearsToReachTargetMargin();
        double marginalTaxRate = input.getMarginalTaxRate() / 100.0;
        double initialCostOfCapital = input.getInitialCostOfCapital() / 100.0;
        double terminalCostOfCapital = input.getTerminalCostOfCapital() / 100.0;
        int yearsOfRiskConvergence = input.getYearsOfRiskConvergence();
        double probabilityOfFailure = input.getProbabilityOfFailure() / 100.0;
        double distressProceeds = input.getDistressProceedsPctOfBookOrRevenue() / 100.0; // This is a percentage
        double totalDebt = data.getTotalDebt();
        double cashBalance = data.getCashBalance();
        double commonSharesOutstanding = data.getCommonSharesOutstanding();

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
        double currentRevenue = latestStatement.getRevenue();
        double currentOperatingIncome = latestStatement.getOperatingIncome();
        double currentMargin = (currentRevenue != 0) ? currentOperatingIncome / currentRevenue : 0.0;

        // Call the main calculation method
        double pricePerShare = calculateEquityValue(
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
            input.getReinvestmentAsPctOfRevenue() / 100.0, // Corrected assumption: this is the percentage of incremental revenue reinvested
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
        return output;
    }

    public double projectRevenue(double currentRevenue, int year, double initialRevenueGrowthRate, int nearTermYears, int growthFadePeriod, double terminalGrowthRate) {
        double growthRate;
        if (year <= nearTermYears) {
            growthRate = initialRevenueGrowthRate;
        } else if (year <= nearTermYears + growthFadePeriod) {
            int fadeYears = year - nearTermYears;
            growthRate = initialRevenueGrowthRate - ((initialRevenueGrowthRate - terminalGrowthRate) / growthFadePeriod) * fadeYears;
        } else {
            growthRate = terminalGrowthRate;
        }
        return currentRevenue * (1 + growthRate);
    }

    public double projectOperatingMargin(double currentMargin, int year, int yearsToReachTargetMargin, double targetOperatingMargin) {
        if (yearsToReachTargetMargin <= 0) return targetOperatingMargin; // Avoid division by zero
        if (year >= yearsToReachTargetMargin) return targetOperatingMargin;
        return currentMargin + (targetOperatingMargin - currentMargin) / yearsToReachTargetMargin * year;
    }

    public double computeFCF(double revenue, double incrementalRevenue, double operatingMargin, double marginalTaxRate, double reinvestmentAsPctOfRevenue) {
        double operatingIncome = revenue * operatingMargin;
        // Reinvestment is calculated based on incremental revenue * reinvestment rate
        double reinvestment = incrementalRevenue * reinvestmentAsPctOfRevenue;
        return operatingIncome * (1 - marginalTaxRate) - reinvestment;
    }

    public double discountRate(int year, int yearsOfRiskConvergence, double initialCostOfCapital, double terminalCostOfCapital) {
        if (yearsOfRiskConvergence <= 0) return terminalCostOfCapital; // Avoid division by zero
        if (year >= yearsOfRiskConvergence) return terminalCostOfCapital;
        return initialCostOfCapital - ((initialCostOfCapital - terminalCostOfCapital) / yearsOfRiskConvergence) * year;
    }

    public double terminalValue(double finalFCF, double terminalGrowthRate, double terminalCostOfCapital) {
        // Gordon Growth Model: TV = FCF × (1 + g) / (WACC - g)
        if (terminalCostOfCapital - terminalGrowthRate <= 0) {
            // Handle case where denominator is zero or negative (e.g., if growth rate > discount rate)
            // This would imply infinite value or negative value, which is not realistic in this model.
            // Return a very large number or throw an exception, depending on desired behavior.
            // For now, return 0.0 to avoid unexpected results.
            return 0.0;
        }
        return finalFCF * (1 + terminalGrowthRate) / (terminalCostOfCapital - terminalGrowthRate);
    }

    public double calculateEquityValue(
        double currentRevenue,
        double currentMargin,
        int yearsToProject,
        double initialRevenueGrowthRate,
        int growthFadePeriod,
        double terminalGrowthRate,
        double targetOperatingMargin,
        int yearsToReachTargetMargin,
        double marginalTaxRate,
        double reinvestmentAsPctOfRevenue,
        double initialCostOfCapital,
        double terminalCostOfCapital,
        int yearsOfRiskConvergence,
        double probabilityOfFailure,
        double distressProceeds, // This is a percentage of current revenue or book value, not a rate
        double totalDebt,
        double cashBalance,
        double commonSharesOutstanding,
        List<BalanceSheetYear> sortedBalanceSheets
    ) {
        double revenue = currentRevenue;
        double previousRevenue = currentRevenue;
        double equityValue = 0;
        double fcf = 0;
        double cumulativeDiscountFactor = 1;
        int nearTermYears = 3; // From the JS code

        for (int year = 1; year <= yearsToProject; year++) {
            revenue = projectRevenue(revenue, year, initialRevenueGrowthRate, nearTermYears, growthFadePeriod, terminalGrowthRate);
            double incrementalRevenue = revenue - previousRevenue;
            double margin = projectOperatingMargin(currentMargin, year, yearsToReachTargetMargin, targetOperatingMargin);
            fcf = computeFCF(revenue, incrementalRevenue, margin, marginalTaxRate, reinvestmentAsPctOfRevenue);

            // Cumulative discounting - multiply by this year's discount factor
            double yearDiscountRate = discountRate(year, yearsOfRiskConvergence, initialCostOfCapital, terminalCostOfCapital);
            cumulativeDiscountFactor *= (1 + yearDiscountRate);

            // Discount FCF using cumulative discount factor
            equityValue += fcf / cumulativeDiscountFactor;

            previousRevenue = revenue;
        }

        // Calculate terminal value - handle negative FCF case
        double terminalVal = 0;
        if (fcf > 0) {
            terminalVal = terminalValue(fcf, terminalGrowthRate, terminalCostOfCapital);
            // Discount terminal value using cumulative discount factor
            equityValue += terminalVal / cumulativeDiscountFactor;
        } else {
            // If FCF is negative, use liquidation value approach (conservative)
            // Value of assets if company were liquidated = Total Assets - Total Debt (per book value)
            // Use the latest year's data
            if (sortedBalanceSheets.isEmpty()) {
                // Should not happen if check at the beginning of calculateIntrinsicValue passes, but defensive coding.
                return 0.0;
            }
            BalanceSheetYear latestBalanceSheet = sortedBalanceSheets.get(sortedBalanceSheets.size() - 1);
            double liquidationValue = (latestBalanceSheet.getTotalAssets() - totalDebt);
            // Discount liquidation value and add to equity
            equityValue += liquidationValue / cumulativeDiscountFactor;
        }

        // Calculate distress value as percentage of current revenue
        double distressValue = currentRevenue * distressProceeds; // distressProceeds is already a percentage

        // Adjust for failure probability
        equityValue = (1 - probabilityOfFailure) * equityValue + probabilityOfFailure * distressValue;

        // Subtract debt, add cash
        if (commonSharesOutstanding == 0) {
            return 0.0; // Avoid division by zero
        }
        return (equityValue - totalDebt + cashBalance) / commonSharesOutstanding;
    }
}
