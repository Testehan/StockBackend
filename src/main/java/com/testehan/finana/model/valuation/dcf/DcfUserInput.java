package com.testehan.finana.model.valuation.dcf;

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
    private Double fcfGrowthRate;
    private Integer terminalMultiple;
    private Boolean sbcAdjustmentToggle;
    private String userComments;
}
