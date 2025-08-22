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
    private BigDecimal operatingProfitMargin;      // EBIT / Revenue (True operational performance)
    private BigDecimal ebitdaMargin;               // Proxy for operating cash flow margin
    private BigDecimal roic; // Added ROIC

    // Liquidity
    private BigDecimal currentRatio;
    private BigDecimal quickRatio;
    private BigDecimal cashRatio;
    private BigDecimal workingCapital;

    // Solvency
    private BigDecimal debtToAssetsRatio;
    private BigDecimal debtToEquityRatio;
    private BigDecimal interestCoverageRatio;      // EBIT / Interest Expense (Can they pay interest?)
    private BigDecimal debtServiceCoverageRatio;
    private BigDecimal netDebtToEbitda;            // How many years to pay off debt? (Industry standard)
    private BigDecimal altmanZScore;               // Bankruptcy prediction score

    // Efficiency
    private BigDecimal assetTurnover;
    private BigDecimal inventoryTurnover;
    private BigDecimal receivablesTurnover;        // How fast do they collect cash?
    private BigDecimal daysSalesOutstanding;       // DSO (Turnover expressed in days)
    private BigDecimal payablesTurnover;           // How fast do they pay suppliers?
    private BigDecimal cashConversionCycle;        // Days to convert inventory to cash (The holy grail of efficiency)
    private BigDecimal daysInventoryOutstanding;
    private BigDecimal daysPayablesOutstanding;

    // Shareholder Returns
    private BigDecimal dividendYield;
    private BigDecimal dividendPayoutRatio;        // % of earnings paid as dividends (Sustainability)
    private BigDecimal buybackYield;               // % of shares repurchased

    // Per Share Metrics
    private BigDecimal earningsPerShareBasic;      // (Net Income / Shares Outstanding)
    private BigDecimal earningsPerShareDiluted;    // (Net Income / (Shares + Options + Warrants))
    private BigDecimal bookValuePerShare;          // (Shareholders Equity / Shares Outstanding)
    private BigDecimal salesPerShare;              // (Revenue / Shares Outstanding)

    // Cash & Returns Per Share
    private BigDecimal freeCashFlowPerShare;       // (Free Cash Flow / Shares Outstanding)
    private BigDecimal operatingCashFlowPerShare;  // (Cash from Ops / Shares Outstanding)
    private BigDecimal dividendPerShare;           // (Total Dividends / Shares Outstanding)
    private BigDecimal cashPerShare;               // (Total Cash on Hand / Shares Outstanding)

    // Cash Flow
    private BigDecimal freeCashFlow; // Added Free Cash Flow
    private BigDecimal operatingCashFlowRatio;
    private BigDecimal cashFlowToDebtRatio;


    // Advanced Valuation
    private BigDecimal tangibleBookValuePerShare;  // ((Equity - Goodwill - Intangibles) / Shares)

}
