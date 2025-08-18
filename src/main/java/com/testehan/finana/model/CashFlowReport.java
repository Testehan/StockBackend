package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CashFlowReport {
    @JsonProperty("fiscalDateEnding")
    private String fiscalDateEnding;
    @JsonProperty("reportedCurrency")
    private String reportedCurrency;
    @JsonProperty("operatingCashflow")
    private String operatingCashflow;
    @JsonProperty("paymentsForOperatingActivities")
    private String paymentsForOperatingActivities;
    @JsonProperty("proceedsFromOperatingActivities")
    private String proceedsFromOperatingActivities;
    @JsonProperty("changeInOperatingLiabilities")
    private String changeInOperatingLiabilities;
    @JsonProperty("changeInOperatingAssets")
    private String changeInOperatingAssets;
    @JsonProperty("depreciationDepletionAndAmortization")
    private String depreciationDepletionAndAmortization;
    @JsonProperty("capitalExpenditures")
    private String capitalExpenditures;
    @JsonProperty("changeInReceivables")
    private String changeInReceivables;
    @JsonProperty("changeInInventory")
    private String changeInInventory;
    @JsonProperty("profitLoss")
    private String profitLoss;
    @JsonProperty("cashflowFromInvestment")
    private String cashflowFromInvestment;
    @JsonProperty("cashflowFromFinancing")
    private String cashflowFromFinancing;
    @JsonProperty("proceedsFromRepaymentsOfShortTermDebt")
    private String proceedsFromRepaymentsOfShortTermDebt;
    @JsonProperty("paymentsForRepurchaseOfCommonStock")
    private String paymentsForRepurchaseOfCommonStock;
    @JsonProperty("paymentsForRepurchaseOfEquity")
    private String paymentsForRepurchaseOfEquity;
    @JsonProperty("paymentsForRepurchaseOfPreferredStock")
    private String paymentsForRepurchaseOfPreferredStock;
    @JsonProperty("dividendPayout")
    private String dividendPayout;
    @JsonProperty("dividendPayoutCommonStock")
    private String dividendPayoutCommonStock;
    @JsonProperty("dividendPayoutPreferredStock")
    private String dividendPayoutPreferredStock;
    @JsonProperty("proceedsFromIssuanceOfCommonStock")
    private String proceedsFromIssuanceOfCommonStock;
    @JsonProperty("proceedsFromIssuanceOfLongTermDebtAndCapitalSecuritiesNet")
    private String proceedsFromIssuanceOfLongTermDebtAndCapitalSecuritiesNet;
    @JsonProperty("proceedsFromIssuanceOfPreferredStock")
    private String proceedsFromIssuanceOfPreferredStock;
    @JsonProperty("proceedsFromRepurchaseOfEquity")
    private String proceedsFromRepurchaseOfEquity;
    @JsonProperty("proceedsFromSaleOfTreasuryStock")
    private String proceedsFromSaleOfTreasuryStock;
    @JsonProperty("changeInCashAndCashEquivalents")
    private String changeInCashAndCashEquivalents;
    @JsonProperty("changeInExchangeRate")
    private String changeInExchangeRate;
    @JsonProperty("netIncome")
    private String netIncome;
}
