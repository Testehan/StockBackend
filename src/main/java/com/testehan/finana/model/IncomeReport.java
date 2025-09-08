package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IncomeReport {
    @JsonProperty("date")
    private String date;
    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("reportedCurrency")
    private String reportedCurrency;
    @JsonProperty("cik")
    private String cik;
    @JsonProperty("filingDate")
    private String filingDate;
    @JsonProperty("acceptedDate")
    private String acceptedDate;
    @JsonProperty("fiscalYear")
    private String fiscalYear;
    @JsonProperty("period")
    private String period;
    @JsonProperty("revenue")
    private String revenue;
    @JsonProperty("costOfRevenue")
    private String costOfRevenue;
    @JsonProperty("grossProfit")
    private String grossProfit;
    @JsonProperty("researchAndDevelopmentExpenses")
    private String researchAndDevelopmentExpenses;
    @JsonProperty("generalAndAdministrativeExpenses")
    private String generalAndAdministrativeExpenses;
    @JsonProperty("sellingAndMarketingExpenses")
    private String sellingAndMarketingExpenses;
    @JsonProperty("sellingGeneralAndAdministrativeExpenses")
    private String sellingGeneralAndAdministrativeExpenses;
    @JsonProperty("otherExpenses")
    private String otherExpenses;
    @JsonProperty("operatingExpenses")
    private String operatingExpenses;
    @JsonProperty("costAndExpenses")
    private String costAndExpenses;
    @JsonProperty("netInterestIncome")
    private String netInterestIncome;
    @JsonProperty("interestIncome")
    private String interestIncome;
    @JsonProperty("interestExpense")
    private String interestExpense;
    @JsonProperty("depreciationAndAmortization")
    private String depreciationAndAmortization;
    @JsonProperty("ebitda")
    private String ebitda;
    @JsonProperty("ebit")
    private String ebit;
    @JsonProperty("nonOperatingIncomeExcludingInterest")
    private String nonOperatingIncomeExcludingInterest;
    @JsonProperty("operatingIncome")
    private String operatingIncome;
    @JsonProperty("totalOtherIncomeExpensesNet")
    private String totalOtherIncomeExpensesNet;
    @JsonProperty("incomeBeforeTax")
    private String incomeBeforeTax;
    @JsonProperty("incomeTaxExpense")
    private String incomeTaxExpense;
    @JsonProperty("netIncomeFromContinuingOperations")
    private String netIncomeFromContinuingOperations;
    @JsonProperty("netIncomeFromDiscontinuedOperations")
    private String netIncomeFromDiscontinuedOperations;
    @JsonProperty("otherAdjustmentsToNetIncome")
    private String otherAdjustmentsToNetIncome;
    @JsonProperty("netIncome")
    private String netIncome;
    @JsonProperty("netIncomeDeductions")
    private String netIncomeDeductions;
    @JsonProperty("bottomLineNetIncome")
    private String bottomLineNetIncome;
    @JsonProperty("eps")
    private String eps;
    @JsonProperty("epsDiluted")
    private String epsDiluted;
    @JsonProperty("weightedAverageShsOut")
    private String weightedAverageShsOut;
    @JsonProperty("weightedAverageShsOutDil")
    private String weightedAverageShsOutDil;
}
