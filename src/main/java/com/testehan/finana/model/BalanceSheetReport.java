package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BalanceSheetReport {
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
    @JsonProperty("cashAndCashEquivalents")
    private String cashAndCashEquivalents;
    @JsonProperty("shortTermInvestments")
    private String shortTermInvestments;
    @JsonProperty("cashAndShortTermInvestments")
    private String cashAndShortTermInvestments;
    @JsonProperty("netReceivables")
    private String netReceivables;
    @JsonProperty("accountsReceivables")
    private String accountsReceivables;
    @JsonProperty("otherReceivables")
    private String otherReceivables;
    @JsonProperty("inventory")
    private String inventory;
    @JsonProperty("prepaids")
    private String prepaids;
    @JsonProperty("otherCurrentAssets")
    private String otherCurrentAssets;
    @JsonProperty("totalCurrentAssets")
    private String totalCurrentAssets;
    @JsonProperty("propertyPlantEquipmentNet")
    private String propertyPlantEquipmentNet;
    @JsonProperty("goodwill")
    private String goodwill;
    @JsonProperty("intangibleAssets")
    private String intangibleAssets;
    @JsonProperty("goodwillAndIntangibleAssets")
    private String goodwillAndIntangibleAssets;
    @JsonProperty("longTermInvestments")
    private String longTermInvestments;
    @JsonProperty("taxAssets")
    private String taxAssets;
    @JsonProperty("otherNonCurrentAssets")
    private String otherNonCurrentAssets;
    @JsonProperty("totalNonCurrentAssets")
    private String totalNonCurrentAssets;
    @JsonProperty("otherAssets")
    private String otherAssets;
    @JsonProperty("totalAssets")
    private String totalAssets;
    @JsonProperty("totalPayables")
    private String totalPayables;
    @JsonProperty("accountPayables")
    private String accountPayables;
    @JsonProperty("otherPayables")
    private String otherPayables;
    @JsonProperty("accruedExpenses")
    private String accruedExpenses;
    @JsonProperty("shortTermDebt")
    private String shortTermDebt;
    @JsonProperty("capitalLeaseObligationsCurrent")
    private String capitalLeaseObligationsCurrent;
    @JsonProperty("taxPayables")
    private String taxPayables;
    @JsonProperty("deferredRevenue")
    private String deferredRevenue;
    @JsonProperty("otherCurrentLiabilities")
    private String otherCurrentLiabilities;
    @JsonProperty("totalCurrentLiabilities")
    private String totalCurrentLiabilities;
    @JsonProperty("longTermDebt")
    private String longTermDebt;
    @JsonProperty("deferredRevenueNonCurrent")
    private String deferredRevenueNonCurrent;
    @JsonProperty("deferredTaxLiabilitiesNonCurrent")
    private String deferredTaxLiabilitiesNonCurrent;
    @JsonProperty("otherNonCurrentLiabilities")
    private String otherNonCurrentLiabilities;
    @JsonProperty("totalNonCurrentLiabilities")
    private String totalNonCurrentLiabilities;
    @JsonProperty("otherLiabilities")
    private String otherLiabilities;
    @JsonProperty("capitalLeaseObligations")
    private String capitalLeaseObligations;
    @JsonProperty("totalLiabilities")
    private String totalLiabilities;
    @JsonProperty("treasuryStock")
    private String treasuryStock;
    @JsonProperty("preferredStock")
    private String preferredStock;
    @JsonProperty("commonStock")
    private String commonStock;
    @JsonProperty("retainedEarnings")
    private String retainedEarnings;
    @JsonProperty("additionalPaidInCapital")
    private String additionalPaidInCapital;
    @JsonProperty("accumulatedOtherComprehensiveIncomeLoss")
    private String accumulatedOtherComprehensiveIncomeLoss;
    @JsonProperty("otherTotalStockholdersEquity")
    private String otherTotalStockholdersEquity;
    @JsonProperty("totalStockholdersEquity")
    private String totalStockholdersEquity;
    @JsonProperty("totalEquity")
    private String totalEquity;
    @JsonProperty("minorityInterest")
    private String minorityInterest;
    @JsonProperty("totalLiabilitiesAndTotalEquity")
    private String totalLiabilitiesAndTotalEquity;
    @JsonProperty("totalInvestments")
    private String totalInvestments;
    @JsonProperty("totalDebt")
    private String totalDebt;
    @JsonProperty("netDebt")
    private String netDebt;
}

