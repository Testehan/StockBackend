package com.testehan.finana.model.adjustment;

import lombok.Data;

@Data
public class FinancialAdjustmentReport {
    private int calendarYear;
    private String date;

    // Reported (Normal) Metrics
    private String reportedOperatingIncome;
    private String reportedNetIncome;
    private String reportedEps;
    private String reportedFreeCashFlow;
    private String reportedEbitda;
    private String reportedRoic;
    private String reportedPe;
    private String reportedInvestedCapital;
    private String reportedBookValueOfEquity;
    private String reportedNopat;
    private String reportedSalesToCapital;
    private String reportedNetDebtToEbitda;
    private String reportedEbitToInterest;
    private String reportedEvToEbitda;

    // Adjustments
    private String adjustedOperatingIncome;
    private String adjustedInvestedCapital;      // Reported Invested Capital + Research Asset
    private String adjustedRoic;            // The "True" Profitability
    private String adjustedSalesToCapital;          // Efficiency of the "Invisible" engine
    private String adjustedNetIncome;       // For the "Corrected" P/E ratio
    private String adjustedMarginalTaxRate; // Cash Tax Rate
    private String adjustedBookValueOfEquity; // Stated book value of equity + Capital invested in R&D
    private String adjustedNopat;
    private String adjustedEps;
    private String adjustedFreeCashFlow;
    private String adjustedEbitda;
    private String adjustedNetDebtToEbitda;
    private String adjustedEbitToInterest;
    private String adjustedPe;
    private String adjustedEvToEbitda;

    // Fields for recalculating price-dependent metrics
    private String weightedAverageShsOutDil;
    private String totalDebt;
    private String cashAndCashEquivalents;
}
