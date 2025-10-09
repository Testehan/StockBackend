package com.testehan.finana.model.valuation;

import lombok.Data;

@Data
public class DcfUserInput {
    private Double beta;
    private Double riskFreeRate;
    private Double marketRiskPremium;
    private Double effectiveTaxRate;
    private Double projectedRevenueGrowthRate;
    private Double projectedEbitMargin;
    private Double perpetualGrowthRate;
    private String userComments;
}
