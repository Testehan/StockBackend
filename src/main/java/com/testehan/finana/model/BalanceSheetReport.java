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

    @JsonProperty("cashAndShortTermInvestments")
    private String cashAndShortTermInvestments;

    @JsonProperty("inventory")
    private String inventory;

    @JsonProperty("currentNetReceivables")
    private String currentNetReceivables;

    @JsonProperty("totalNonCurrentAssets")
    private String totalNonCurrentAssets;

    @JsonProperty("propertyPlantEquipment")
    private String propertyPlantEquipment;

    @JsonProperty("accumulatedDepreciationAmortizationPPE")
    private String accumulatedDepreciationAmortizationPPE;

    @JsonProperty("intangibleAssets")
    private String intangibleAssets;

    @JsonProperty("intangibleAssetsExcludingGoodwill")
    private String intangibleAssetsExcludingGoodwill;

    @JsonProperty("goodwill")
    private String goodwill;

    @JsonProperty("investments")
    private String investments;

    @JsonProperty("longTermInvestments")
    private String longTermInvestments;

    @JsonProperty("shortTermInvestments")
    private String shortTermInvestments;

    @JsonProperty("otherCurrentAssets")
    private String otherCurrentAssets;

    @JsonProperty("otherNonCurrentAssets")
    private String otherNonCurrentAssets;

    @JsonProperty("totalLiabilities")
    private String totalLiabilities;

    @JsonProperty("totalCurrentLiabilities")
    private String totalCurrentLiabilities;

    @JsonProperty("currentAccountsPayable")
    private String currentAccountsPayable;

    @JsonProperty("deferredRevenue")
    private String deferredRevenue;

    @JsonProperty("currentDebt")
    private String currentDebt;

    @JsonProperty("shortTermDebt")
    private String shortTermDebt;

    @JsonProperty("totalNonCurrentLiabilities")
    private String totalNonCurrentLiabilities;

    @JsonProperty("capitalLeaseObligations")
    private String capitalLeaseObligations;

    @JsonProperty("longTermDebt")
    private String longTermDebt;

    @JsonProperty("currentLongTermDebt")
    private String currentLongTermDebt;

    @JsonProperty("longTermDebtNoncurrent")
    private String longTermDebtNoncurrent;

    @JsonProperty("shortLongTermDebtTotal")
    private String shortLongTermDebtTotal;

    @JsonProperty("otherCurrentLiabilities")
    private String otherCurrentLiabilities;

    @JsonProperty("otherNonCurrentLiabilities")
    private String otherNonCurrentLiabilities;

    @JsonProperty("totalShareholderEquity")
    private String totalShareholderEquity;

    @JsonProperty("treasuryStock")
    private String treasuryStock;

    @JsonProperty("retainedEarnings")
    private String retainedEarnings;

    @JsonProperty("commonStock")
    private String commonStock;

    @JsonProperty("commonStockSharesOutstanding")
    private String commonStockSharesOutstanding;
}

