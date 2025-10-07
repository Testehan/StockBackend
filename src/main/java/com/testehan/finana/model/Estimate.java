package com.testehan.finana.model;

import lombok.Data;

@Data
public class Estimate {
    private String symbol;
    private String date;
    private String revenueLow;
    private String revenueHigh;
    private String revenueAvg;
    private String ebitdaLow;
    private String ebitdaHigh;
    private String ebitdaAvg;
    private String ebitLow;
    private String ebitHigh;
    private String ebitAvg;
    private String netIncomeLow;
    private String netIncomeHigh;
    private String netIncomeAvg;
    private String sgaExpenseLow;
    private String sgaExpenseHigh;
    private String sgaExpenseAvg;
    private String epsAvg;
    private String epsHigh;
    private String epsLow;
    private Integer numAnalystsRevenue;
    private Integer numAnalystsEps;
}
