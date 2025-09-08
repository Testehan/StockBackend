package com.testehan.finana.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CashFlowReport {
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
    @JsonProperty("netIncome")
    private String netIncome;
    @JsonProperty("depreciationAndAmortization")
    private String depreciationAndAmortization;
    @JsonProperty("deferredIncomeTax")
    private String deferredIncomeTax;
    @JsonProperty("stockBasedCompensation")
    private String stockBasedCompensation;
    @JsonProperty("changeInWorkingCapital")
    private String changeInWorkingCapital;
    @JsonProperty("accountsReceivables")
    private String accountsReceivables;
    @JsonProperty("inventory")
    private String inventory;
    @JsonProperty("accountsPayables")
    private String accountsPayables;
    @JsonProperty("otherWorkingCapital")
    private String otherWorkingCapital;
    @JsonProperty("otherNonCashItems")
    private String otherNonCashItems;
    @JsonProperty("netCashProvidedByOperatingActivities")
    private String netCashProvidedByOperatingActivities;
    @JsonProperty("investmentsInPropertyPlantAndEquipment")
    private String investmentsInPropertyPlantAndEquipment;
    @JsonProperty("acquisitionsNet")
    private String acquisitionsNet;
    @JsonProperty("purchasesOfInvestments")
    private String purchasesOfInvestments;
    @JsonProperty("salesMaturitiesOfInvestments")
    private String salesMaturitiesOfInvestments;
    @JsonProperty("otherInvestingActivities")
    private String otherInvestingActivities;
    @JsonProperty("netCashProvidedByInvestingActivities")
    private String netCashProvidedByInvestingActivities;
    @JsonProperty("netDebtIssuance")
    private String netDebtIssuance;
    @JsonProperty("longTermNetDebtIssuance")
    private String longTermNetDebtIssuance;
    @JsonProperty("shortTermNetDebtIssuance")
    private String shortTermNetDebtIssuance;
    @JsonProperty("netStockIssuance")
    private String netStockIssuance;
    @JsonProperty("netCommonStockIssuance")
    private String netCommonStockIssuance;
    @JsonProperty("commonStockIssuance")
    private String commonStockIssuance;
    @JsonProperty("commonStockRepurchased")
    private String commonStockRepurchased;
    @JsonProperty("netPreferredStockIssuance")
    private String netPreferredStockIssuance;
    @JsonProperty("netDividendsPaid")
    private String netDividendsPaid;
    @JsonProperty("commonDividendsPaid")
    private String commonDividendsPaid;
    @JsonProperty("preferredDividendsPaid")
    private String preferredDividendsPaid;
    @JsonProperty("otherFinancingActivities")
    private String otherFinancingActivities;
    @JsonProperty("netCashProvidedByFinancingActivities")
    private String netCashProvidedByFinancingActivities;
    @JsonProperty("effectOfForexChangesOnCash")
    private String effectOfForexChangesOnCash;
    @JsonProperty("netChangeInCash")
    private String netChangeInCash;
    @JsonProperty("cashAtEndOfPeriod")
    private String cashAtEndOfPeriod;
    @JsonProperty("cashAtBeginningOfPeriod")
    private String cashAtBeginningOfPeriod;
    @JsonProperty("operatingCashFlow")
    private String operatingCashFlow;
    @JsonProperty("capitalExpenditure")
    private String capitalExpenditure;
    @JsonProperty("freeCashFlow")
    private String freeCashFlow;
    @JsonProperty("incomeTaxesPaid")
    private String incomeTaxesPaid;
    @JsonProperty("interestPaid")
    private String interestPaid;
}
