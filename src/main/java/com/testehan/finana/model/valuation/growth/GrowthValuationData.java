package com.testehan.finana.model.valuation.growth;

import lombok.Data;

import java.math.BigDecimal;
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
    private BigDecimal currentSharePrice;
    private BigDecimal marketCapitalization;
    private BigDecimal riskFreeRate;

    // Financial Statements
    private List<IncomeStatementYear> incomeStatements;
    private List<BalanceSheetYear> balanceSheets;
    private List<CashFlowYear> cashFlows;

    // Tax Attributes
    // TODO THE NEXT 2 are set to 0...this is because the information about them is not available in the income statements...
    // it is somewhere in the sec fillings so it should be extracted from there...or given as input fields
    // either way right now the react client code does not use them...but uses marginalTaxRate
    private BigDecimal netOperatingLossCarryforward;
    private int nolExpirationYears;

    // Capital Structure
    private BigDecimal totalDebt;
    private BigDecimal averageInterestRate;
    private BigDecimal cashBalance;

    // Share Counts
    private BigDecimal commonSharesOutstanding;
}
