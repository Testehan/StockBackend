package com.testehan.finana.model.valuation.growth;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GrowthUserInput {
    private BigDecimal initialRevenueGrowthRate = BigDecimal.ZERO; // Initial Revenue Growth Rate (%)
    private Integer growthFadePeriod = 5;       // Growth Fade Period (years)
    private BigDecimal terminalGrowthRate = BigDecimal.valueOf(2);     // Terminal Growth Rate (%)
    private Integer yearsToProject = 10;         // Years to Project
    private BigDecimal targetOperatingMargin = BigDecimal.ZERO;  // Target Operating Margin (%)
    private Integer yearsToReachTargetMargin = 10; // Years to Reach Target Margin
    private BigDecimal reinvestmentAsPctOfRevenue = BigDecimal.ZERO; // Reinvestment as % of Revenue
    private BigDecimal initialCostOfCapital = BigDecimal.valueOf(10);   // Initial Cost of Capital (%)
    private BigDecimal terminalCostOfCapital = BigDecimal.valueOf(8);  // Terminal Cost of Capital (%)
    private Integer yearsOfRiskConvergence = 10; // Years of Risk Convergence
    private BigDecimal probabilityOfFailure = BigDecimal.ZERO;   // Probability of Failure (%)
    private BigDecimal distressProceedsPctOfBookOrRevenue = BigDecimal.ZERO; // Distress Proceeds (% of book or revenue)
    private BigDecimal marginalTaxRate = BigDecimal.valueOf(21);
}
