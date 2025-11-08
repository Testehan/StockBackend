package com.testehan.finana.model.valuation.growth;

import lombok.Data;

@Data
public class GrowthUserInput {
    private Double initialRevenueGrowthRate = 0d; // Initial Revenue Growth Rate (%)
    private Integer growthFadePeriod = 5;       // Growth Fade Period (years)
    private Double terminalGrowthRate = 2d;     // Terminal Growth Rate (%)
    private Integer yearsToProject = 10;         // Years to Project
    private Double targetOperatingMargin = 0d;  // Target Operating Margin (%)
    private Integer yearsToReachTargetMargin = 10; // Years to Reach Target Margin
    private Double reinvestmentAsPctOfRevenue = 0d; // Reinvestment as % of Revenue
    private Double initialCostOfCapital = 10d;   // Initial Cost of Capital (%)
    private Double terminalCostOfCapital = 8d;  // Terminal Cost of Capital (%)
    private Integer yearsOfRiskConvergence = 10; // Years of Risk Convergence
    private Double probabilityOfFailure = 0d;   // Probability of Failure (%)
    private Double distressProceedsPctOfBookOrRevenue = 0d; // Distress Proceeds (% of book or revenue)
    private Double marginalTaxRate = 21d;
}
