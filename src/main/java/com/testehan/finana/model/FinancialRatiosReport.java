package com.testehan.finana.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinancialRatiosReport {
    private String fiscalDateEnding;

    // Profitability
    private BigDecimal grossProfitMargin;
    private BigDecimal netProfitMargin;
    private BigDecimal returnOnAssets;
    private BigDecimal returnOnEquity;

    // Liquidity
    private BigDecimal currentRatio;
    private BigDecimal quickRatio;

    // Solvency
    private BigDecimal debtToAssetsRatio;
    private BigDecimal debtToEquityRatio;

    // Efficiency
    private BigDecimal assetTurnover;
    private BigDecimal inventoryTurnover;
}
