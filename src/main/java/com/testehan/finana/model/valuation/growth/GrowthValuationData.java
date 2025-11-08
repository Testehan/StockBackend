package com.testehan.finana.model.valuation.growth;

import lombok.Data;

import java.util.List;

@Data
public class GrowthValuationData {

    // Company Profile
    private String ticker;
    private String name;
    private String sector;
    private String industry;
    private String currency;
//    private String fiscalYearEnd;
//    private int yearsPublic;

    // Market Data
    private double currentSharePrice;
    private double marketCapitalization;
    private double riskFreeRate;

    // Financial Statements
    private List<IncomeStatementYear> incomeStatements;
    private List<BalanceSheetYear> balanceSheets;
    private List<CashFlowYear> cashFlows;

    // Tax Attributes
    // TODO THE NEXT 2 are set to 0...this is because the information about them is not available in the income statements...
    // it is somewhere in the sec fillings so it should be extracted from there...or given as input fields
    // either way right now the react client code does not use them...but uses marginalTaxRate
    private double netOperatingLossCarryforward;
    private int nolExpirationYears;

    // Capital Structure
    private double totalDebt;
    private double averageInterestRate;
    private double cashBalance;

    // Share Counts
    private double commonSharesOutstanding;
}
