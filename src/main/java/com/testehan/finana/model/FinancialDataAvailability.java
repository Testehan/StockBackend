package com.testehan.finana.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDataAvailability {
    private boolean lastStockQuote;
    private boolean incomeStatements;
    private boolean balanceSheet;
    private boolean cashFlow;
    private boolean earningsEstimates;
    private boolean earningsHistory;
    private boolean companyOverview;
    private boolean revenueSegmentation;
    private boolean revenueGeographicSegmentation;
    private boolean secQuarterlyFilings;
    private boolean secAnnualFilings;
    private boolean financialRatios;
    private boolean earningsCallTranscript;
}
