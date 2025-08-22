package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BalanceSheetReport {
    @JsonProperty("fiscalDateEnding")
    private String fiscalDateEnding;
    @JsonProperty("reportedCurrency")
    private String reportedCurrency;
    @JsonProperty("totalAssets")
    private String totalAssets;
    @JsonProperty("totalCurrentAssets")
    private String totalCurrentAssets;
    @JsonProperty("cashAndCashEquivalentsAtCarryingValue")
    private String cashAndCashEquivalentsAtCarryingValue;
    @JsonProperty("shortTermInvestments")
    private String shortTermInvestments;
    @JsonProperty("netReceivables")
    private String netReceivables;
    @JsonProperty("inventory")
    private String inventory;
    @JsonProperty("otherCurrentAssets")
    private String otherCurrentAssets;
    @JsonProperty("propertyPlantEquipment")
    private String propertyPlantEquipment;
    @JsonProperty("goodwill")
    private String goodwill;
    @JsonProperty("intangibleAssets")
    private String intangibleAssets;
    @JsonProperty("longTermInvestments")
    private String longTermInvestments;
    @JsonProperty("otherNonCurrentAssets")
    private String otherNonCurrentAssets;
    @JsonProperty("totalLiabilities")
    private String totalLiabilities;
    @JsonProperty("currentAccountsPayable")
    private String currentAccountsPayable;
    @JsonProperty("deferredRevenue")
    private String deferredRevenue;
    @JsonProperty("shortTermDebt")
    private String shortTermDebt;
    @JsonProperty("otherCurrentLiabilities")
    private String otherCurrentLiabilities;
    @JsonProperty("longTermDebt")
    private String longTermDebt;
    @JsonProperty("otherNonCurrentLiabilities")
    private String otherNonCurrentLiabilities;
    @JsonProperty("totalShareholderEquity")
    private String totalShareholderEquity;
    @JsonProperty("commonStock")
    private String commonStock;
    @JsonProperty("retainedEarnings")
    private String retainedEarnings;
    @JsonProperty("treasuryStock")
    private String treasuryStock;
    @JsonProperty("accumulatedOtherComprehensiveIncome")
    private String accumulatedOtherComprehensiveIncome;
}
