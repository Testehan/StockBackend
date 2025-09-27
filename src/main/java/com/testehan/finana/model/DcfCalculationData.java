package com.testehan.finana.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The main container for all data required to initialize the DCF Calculator.
 *
 * @param meta          Basic company info and market context.
 * @param income        TTM (Trailing Twelve Months) Income Statement data.
 * @param balanceSheet  Most recent Quarter Balance Sheet data.
 * @param cashFlow      TTM Cash Flow Statement data.
 * @param assumptions   Pre-calculated historical averages to help pre-fill UI inputs.
 */
@Builder
public record DcfCalculationData(
    CompanyMeta meta,
    IncomeData income,
    BalanceSheetData balanceSheet,
    CashFlowData cashFlow,
    HistoricalAssumptions assumptions
) {

    /**
     * Market Data and Identity
     */
    @Builder
    public record CompanyMeta(
        String ticker,
        String companyName,
        String currency,            // e.g., "USD"
        BigDecimal currentSharePrice,
        BigDecimal sharesOutstanding,
        LocalDate lastUpdated       // To let the user know how fresh the data is
    ) {}

    /**
     * Income Statement (TTM - Trailing Twelve Months)
     * Used for the baseline of the projection.
     */
    @Builder
    public record IncomeData(
        BigDecimal revenue,
        BigDecimal ebit,            // Operating Income
        BigDecimal interestExpense, // Needed for Cost of Debt
        BigDecimal incomeTaxExpense // Needed for Effective Tax Rate
    ) {}

    /**
     * Balance Sheet (Most Recent Quarter MRQ)
     * Needed for Enterprise Value bridge and WACC.
     */
    @Builder
    public record BalanceSheetData(
        BigDecimal totalCashAndEquivalents,
        BigDecimal totalShortTermDebt,
        BigDecimal totalLongTermDebt,

        // Needed for Net Working Capital (NWC) Calculation
        BigDecimal totalCurrentAssets,
        BigDecimal totalCurrentLiabilities
    ) {}

    /**
     * Cash Flow Statement (TTM)
     * Needed to bridge EBIT to Free Cash Flow.
     */
    @Builder
    public record CashFlowData(
        BigDecimal depreciationAndAmortization,
        BigDecimal capitalExpenditure,         // Usually a negative number, check your sign logic!
        BigDecimal stockBasedCompensation      // Optional: Many analysts add this back
    ) {}

    /**
     * Data needed for WACC & Defaults
     * These help you pre-fill the calculator "sliders" for the user.
     */
    @Builder
    public record HistoricalAssumptions(
        double beta,                    // Volatility measure for Cost of Equity
        double riskFreeRate,            // e.g., 0.042 (4.2% - 10Y Treasury)
        double marketRiskPremium,       // e.g., 0.055 (5.5%)
        double effectiveTaxRate,        // Calculated: Tax Expense / Pre-Tax Income
        double revenueGrowthCagr3Year,  // Compound Annual Growth Rate (3y)
        double averageEbitMargin3Year   // To set a realistic margin target
    ) {}
}
