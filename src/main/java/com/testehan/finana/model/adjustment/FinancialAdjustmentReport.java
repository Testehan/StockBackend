package com.testehan.finana.model.adjustment;

public class FinancialAdjustmentReport {
    private String date;
    private int calendarYear;

    // --- R&D CAPITALIZATION BRIDGE ---
    private Double reportedEbit;           // Starting Point
    private Double currentRDExpense;       // Add back
    private Double rdAmortization;         // Subtract
    private Double adjustedEbit;           // Result Point B

    // --- THE "INVISIBLE" ASSETS ---
    private Double researchAssetValue;     // The "Invisible" asset on Balance Sheet
    private Double adjustedBookValue;      // Reported Equity + Research Asset

    // --- ADJUSTED PERFORMANCE METRICS ---
    private Double adjustedRoic;           // (Adj EBIT * (1-t)) / (Adj Book Value + Debt)
    private Double salesToCapitalRatio;    // Revenue / (Adj Invested Capital)
}
