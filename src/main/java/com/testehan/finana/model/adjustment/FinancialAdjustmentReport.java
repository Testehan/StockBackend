package com.testehan.finana.model.adjustment;

import lombok.Data;

@Data
public class FinancialAdjustmentReport {
    private int calendarYear;
    private String date;

    // R&D Capitalization
    private String reportedOperatingIncome; // EBIT
    private String addBackCurrentRd;
    private String subtractRdAmortization;
    private String rdCapitalizationAdjustment;
    private String adjustedOperatingIncome;
    private String researchAsset;
}
