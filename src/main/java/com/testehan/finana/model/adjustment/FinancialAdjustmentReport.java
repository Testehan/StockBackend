package com.testehan.finana.model.adjustment;

import lombok.Data;

@Data
public class FinancialAdjustmentReport {
    private int calendarYear;
    private String date;

    // R&D Capitalization
    private String rdCapitalizationAdjustment; // Retaining this as it represents the "bridge" component
    private String adjustedOperatingIncome;

    private String adjInvestedCapital;      // Reported Invested Capital + Research Asset
    private String adjustedRoic;            // The "True" Profitability
    private String salesToCapital;          // Efficiency of the "Invisible" engine
    private String adjustedNetIncome;       // For the "Corrected" P/E ratio

    private String adjustedMarginalTaxRate; // Cash Tax Rate
    private String dilutedNetIncome;

    private String adjustedBookValueOfEquity; // Stated book value of equity + Capital invested in R&D
    private String adjustedNopat;
    private String adjustedEps;

    private String adjustedFreeCashFlow;
    private String adjustedEbitda;
    private String netDebtToAdjustedEbitda;
    private String adjustedEbitToInterest;
    private String adjustedPe;
    private String evToAdjustedEbitda;
}
