package com.testehan.finana.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@Document(collection = "financial_ratios")
public class FinancialRatios {
    @Id
    private String id;
    private String symbol;
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
