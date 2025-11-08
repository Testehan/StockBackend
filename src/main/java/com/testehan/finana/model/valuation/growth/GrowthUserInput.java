package com.testehan.finana.model.valuation.growth;

import lombok.Data;

@Data
public class GrowthUserInput {
    private Double initialRevenueGrowthRate; // Initial Revenue Growth Rate (%)
    private Integer growthFadePeriod;       // Growth Fade Period (years)
    private Double terminalGrowthRate;     // Terminal Growth Rate (%)
    private Integer yearsToProject;         // Years to Project
    private Double targetOperatingMargin;  // Target Operating Margin (%)
    private Integer yearsToReachTargetMargin; // Years to Reach Target Margin
    private Double reinvestmentAsPctOfRevenue; // Reinvestment as % of Revenue
    private Double initialCostOfCapital;   // Initial Cost of Capital (%)
    private Double terminalCostOfCapital;  // Terminal Cost of Capital (%)
    private Integer yearsOfRiskConvergence; // Years of Risk Convergence
    private Double probabilityOfFailure;   // Probability of Failure (%)
    private Double distressProceedsPctOfBookOrRevenue; // Distress Proceeds (% of book or revenue)
    private Double marginalTaxRate;
}
