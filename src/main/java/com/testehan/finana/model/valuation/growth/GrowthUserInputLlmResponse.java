package com.testehan.finana.model.valuation.growth;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GrowthUserInputLlmResponse extends GrowthUserInput {
    private String initialRevenueGrowthRateExplanation;
    private String growthFadePeriodExplanation;
    private String terminalGrowthRateExplanation;
    private String yearsToProjectExplanation;
    private String targetOperatingMarginExplanation;
    private String yearsToReachTargetMarginExplanation;
    private String reinvestmentAsPctOfRevenueExplanation;
    private String initialCostOfCapitalExplanation;
    private String terminalCostOfCapitalExplanation;
    private String yearsOfRiskConvergenceExplanation;
    private String probabilityOfFailureExplanation;
    private String distressProceedsPctOfBookOrRevenueExplanation;
    private String marginalTaxRateExplanation;
}
