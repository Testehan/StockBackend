package com.testehan.finana.util.data;

import com.testehan.finana.model.BalanceSheetReport;
import com.testehan.finana.model.CashFlowReport;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.util.SafeParser;

import java.math.BigDecimal;

/**
 * Immutable data holder for parsed financial data from company reports.
 * Extracted from FinancialRatiosCalculator to improve separation of concerns.
 */
public class ParsedFinancialData {

    // Market Cap
    public final BigDecimal marketCap;
    public final BigDecimal stockPrice;

    // Income Statement
    public final BigDecimal totalRevenue;
    public final BigDecimal grossProfit;
    public final BigDecimal costOfRevenue;
    public final BigDecimal operatingIncome;
    public final BigDecimal netIncome;
    public final BigDecimal ebit;
    public final BigDecimal ebitda;
    public final BigDecimal incomeTaxExpense;
    public final BigDecimal incomeBeforeTax;
    public final BigDecimal interestExpense;
    public final BigDecimal eps;
    public final BigDecimal epsDiluted;

    // Balance Sheet - Assets (Current)
    public final BigDecimal totalAssets;
    public final BigDecimal totalCurrentAssets;
    public final BigDecimal cash;
    public final BigDecimal shortTermInvestments;
    public final BigDecimal netReceivables;
    public final BigDecimal inventory;
    public final BigDecimal otherCurrentAssets;
    public final BigDecimal minorityInterest;

    // Balance Sheet - Assets (Non-Current)
    public final BigDecimal propertyPlantEquipment;
    public final BigDecimal goodwill;
    public final BigDecimal intangibleAssets;
    public final BigDecimal longTermInvestments;
    public final BigDecimal otherNonCurrentAssets;

    // Balance Sheet - Liabilities (Current)
    public final BigDecimal totalLiabilities;
    public final BigDecimal currentAccountsPayable;
    public final BigDecimal deferredRevenue;
    public final BigDecimal shortTermDebt;
    public final BigDecimal otherCurrentLiabilities;

    // Balance Sheet - Liabilities (Non-Current)
    public final BigDecimal longTermDebt;
    public final BigDecimal otherNonCurrentLiabilities;
    public final BigDecimal capitalLeaseObligations;

    // Balance Sheet - Equity
    public final BigDecimal totalShareholderEquity;
    public final BigDecimal commonStock;
    public final BigDecimal retainedEarnings;
    public final BigDecimal treasuryStock;
    public final BigDecimal accumulatedOtherComprehensiveIncome;

    // Cash Flow Statement
    public final BigDecimal operatingCashflow;
    public final BigDecimal capitalExpenditures;
    public final BigDecimal dividendPayout;
    public final BigDecimal dividendPayoutCommonStock;
    public final BigDecimal dividendPayoutPreferredStock;
    public final BigDecimal stockBasedCompensation;
    public final BigDecimal commonStockRepurchased;

    // Shares
    public final BigDecimal sharesOutstanding;
    public final BigDecimal sharesOutstandingBasic;

    // Computed values
    public final BigDecimal totalDebt;
    public final BigDecimal netDebt;
    public final BigDecimal currentLiabilities;
    public final BigDecimal quickAssets;
    public final BigDecimal tangibleEquity;

    private ParsedFinancialData(Builder builder) {
        this.marketCap = builder.marketCap;
        this.stockPrice = builder.stockPrice;
        this.totalRevenue = builder.totalRevenue;
        this.grossProfit = builder.grossProfit;
        this.costOfRevenue = builder.costOfRevenue;
        this.operatingIncome = builder.operatingIncome;
        this.netIncome = builder.netIncome;
        this.ebit = builder.ebit;
        this.ebitda = builder.ebitda;
        this.incomeTaxExpense = builder.incomeTaxExpense;
        this.incomeBeforeTax = builder.incomeBeforeTax;
        this.interestExpense = builder.interestExpense;
        this.eps = builder.eps;
        this.epsDiluted = builder.epsDiluted;
        this.totalAssets = builder.totalAssets;
        this.totalCurrentAssets = builder.totalCurrentAssets;
        this.cash = builder.cash;
        this.shortTermInvestments = builder.shortTermInvestments;
        this.netReceivables = builder.netReceivables;
        this.inventory = builder.inventory;
        this.otherCurrentAssets = builder.otherCurrentAssets;
        this.minorityInterest = builder.minorityInterest;
        this.propertyPlantEquipment = builder.propertyPlantEquipment;
        this.goodwill = builder.goodwill;
        this.intangibleAssets = builder.intangibleAssets;
        this.longTermInvestments = builder.longTermInvestments;
        this.otherNonCurrentAssets = builder.otherNonCurrentAssets;
        this.totalLiabilities = builder.totalLiabilities;
        this.currentAccountsPayable = builder.currentAccountsPayable;
        this.deferredRevenue = builder.deferredRevenue;
        this.shortTermDebt = builder.shortTermDebt;
        this.otherCurrentLiabilities = builder.otherCurrentLiabilities;
        this.longTermDebt = builder.longTermDebt;
        this.otherNonCurrentLiabilities = builder.otherNonCurrentLiabilities;
        this.capitalLeaseObligations = builder.capitalLeaseObligations;
        this.totalShareholderEquity = builder.totalShareholderEquity;
        this.commonStock = builder.commonStock;
        this.retainedEarnings = builder.retainedEarnings;
        this.treasuryStock = builder.treasuryStock;
        this.accumulatedOtherComprehensiveIncome = builder.accumulatedOtherComprehensiveIncome;
        this.operatingCashflow = builder.operatingCashflow;
        this.capitalExpenditures = builder.capitalExpenditures;
        this.dividendPayout = builder.dividendPayout;
        this.dividendPayoutCommonStock = builder.dividendPayoutCommonStock;
        this.dividendPayoutPreferredStock = builder.dividendPayoutPreferredStock;
        this.stockBasedCompensation = builder.stockBasedCompensation;
        this.commonStockRepurchased = builder.commonStockRepurchased;
        this.sharesOutstanding = builder.sharesOutstanding;
        this.sharesOutstandingBasic = builder.sharesOutstandingBasic;
        
        // Computed values
        this.totalDebt = safeAdd(shortTermDebt, longTermDebt);
        this.netDebt = safeSubtract(totalDebt, cash);
        this.currentLiabilities = safeAdd(safeAdd(safeAdd(shortTermDebt, currentAccountsPayable), otherCurrentLiabilities), deferredRevenue);
        this.quickAssets = safeSubtract(totalCurrentAssets, inventory);
        this.tangibleEquity = safeSubtract(safeSubtract(totalShareholderEquity, goodwill), intangibleAssets);
    }

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b);
    }

    private BigDecimal safeSubtract(BigDecimal a, BigDecimal b) {
        if (a == null) return null;
        if (b == null) return a;
        return a.subtract(b);
    }

    public static ParsedFinancialData parse(CompanyOverview companyOverview,
                                           IncomeReport incomeReport,
                                           BalanceSheetReport balanceSheetReport,
                                           CashFlowReport cashFlowReport,
                                           BigDecimal stockPrice) {
        return new Builder(companyOverview, incomeReport, balanceSheetReport, cashFlowReport, stockPrice).build();
    }

    public static ParsedFinancialData parse(CompanyOverview companyOverview,
                                           IncomeReport incomeReport,
                                           BalanceSheetReport balanceSheetReport,
                                           CashFlowReport cashFlowReport) {
        return new Builder(companyOverview, incomeReport, balanceSheetReport, cashFlowReport, null).build();
    }

    private static class Builder {
        private final SafeParser safeParser = new SafeParser();

        // Market Cap
        private BigDecimal marketCap;
        private BigDecimal stockPrice;

        // Income Statement
        private BigDecimal totalRevenue;
        private BigDecimal grossProfit;
        private BigDecimal costOfRevenue;
        private BigDecimal operatingIncome;
        private BigDecimal netIncome;
        private BigDecimal ebit;
        private BigDecimal ebitda;
        private BigDecimal incomeTaxExpense;
        private BigDecimal incomeBeforeTax;
        private BigDecimal interestExpense;
        private BigDecimal eps;
        private BigDecimal epsDiluted;

        // Balance Sheet - Assets (Current)
        private BigDecimal totalAssets;
        private BigDecimal totalCurrentAssets;
        private BigDecimal cash;
        private BigDecimal shortTermInvestments;
        private BigDecimal netReceivables;
        private BigDecimal inventory;
        private BigDecimal otherCurrentAssets;
        private BigDecimal minorityInterest;

        // Balance Sheet - Assets (Non-Current)
        private BigDecimal propertyPlantEquipment;
        private BigDecimal goodwill;
        private BigDecimal intangibleAssets;
        private BigDecimal longTermInvestments;
        private BigDecimal otherNonCurrentAssets;

        // Balance Sheet - Liabilities (Current)
        private BigDecimal totalLiabilities;
        private BigDecimal currentAccountsPayable;
        private BigDecimal deferredRevenue;
        private BigDecimal shortTermDebt;
        private BigDecimal otherCurrentLiabilities;

        // Balance Sheet - Liabilities (Non-Current)
        private BigDecimal longTermDebt;
        private BigDecimal otherNonCurrentLiabilities;
        private BigDecimal capitalLeaseObligations;

        // Balance Sheet - Equity
        private BigDecimal totalShareholderEquity;
        private BigDecimal commonStock;
        private BigDecimal retainedEarnings;
        private BigDecimal treasuryStock;
        private BigDecimal accumulatedOtherComprehensiveIncome;

        // Cash Flow Statement
        private BigDecimal operatingCashflow;
        private BigDecimal capitalExpenditures;
        private BigDecimal dividendPayout;
        private BigDecimal dividendPayoutCommonStock;
        private BigDecimal dividendPayoutPreferredStock;
        private BigDecimal stockBasedCompensation;
        private BigDecimal commonStockRepurchased;

        // Shares
        private BigDecimal sharesOutstanding;
        private BigDecimal sharesOutstandingBasic;

        Builder(CompanyOverview companyOverview,
                IncomeReport income,
                BalanceSheetReport balance,
                CashFlowReport cashFlow,
                BigDecimal stockPrice) {
            
            this.marketCap = safeParser.parse(companyOverview.getMarketCap());
            this.stockPrice = stockPrice;

            // Income Statement
            this.totalRevenue = safeParser.parse(income.getRevenue());
            this.grossProfit = safeParser.parse(income.getGrossProfit());
            this.costOfRevenue = safeParser.parse(income.getCostOfRevenue());
            this.operatingIncome = safeParser.parse(income.getOperatingIncome());
            this.netIncome = safeParser.parse(income.getNetIncome());
            this.ebit = safeParser.parse(income.getEbit());
            this.ebitda = safeParser.parse(income.getEbitda());
            this.incomeTaxExpense = safeParser.parse(income.getIncomeTaxExpense());
            this.incomeBeforeTax = safeParser.parse(income.getIncomeBeforeTax());
            this.interestExpense = safeParser.parse(income.getInterestExpense());
            this.eps = safeParser.parse(income.getEps());
            this.epsDiluted = safeParser.parse(income.getEpsDiluted());

            // Balance Sheet - Assets (Current)
            this.totalAssets = safeParser.parse(balance.getTotalAssets());
            this.totalCurrentAssets = safeParser.parse(balance.getTotalCurrentAssets());
            this.cash = safeParser.parse(balance.getCashAndCashEquivalents());
            this.shortTermInvestments = safeParser.parse(balance.getShortTermInvestments());
            this.netReceivables = safeParser.parse(balance.getNetReceivables());
            this.inventory = safeParser.parse(balance.getInventory());
            this.otherCurrentAssets = safeParser.parse(balance.getOtherCurrentAssets());
            this.minorityInterest = safeParser.parse(balance.getMinorityInterest());

            // Balance Sheet - Assets (Non-Current)
            this.propertyPlantEquipment = safeParser.parse(balance.getPropertyPlantEquipmentNet());
            this.goodwill = safeParser.parse(balance.getGoodwill());
            this.intangibleAssets = safeParser.parse(balance.getIntangibleAssets());
            this.longTermInvestments = safeParser.parse(balance.getLongTermInvestments());
            this.otherNonCurrentAssets = safeParser.parse(balance.getOtherNonCurrentAssets());

            // Balance Sheet - Liabilities (Current)
            this.totalLiabilities = safeParser.parse(balance.getTotalLiabilities());
            this.currentAccountsPayable = safeParser.parse(balance.getAccountPayables());
            this.deferredRevenue = safeParser.parse(balance.getDeferredRevenue());
            this.shortTermDebt = safeParser.parse(balance.getShortTermDebt());
            this.otherCurrentLiabilities = safeParser.parse(balance.getOtherCurrentLiabilities());

            // Balance Sheet - Liabilities (Non-Current)
            this.longTermDebt = safeParser.parse(balance.getLongTermDebt());
            this.otherNonCurrentLiabilities = safeParser.parse(balance.getOtherNonCurrentLiabilities());
            this.capitalLeaseObligations = safeParser.parse(balance.getCapitalLeaseObligations());

            // Balance Sheet - Equity
            this.totalShareholderEquity = safeParser.parse(balance.getTotalStockholdersEquity());
            this.commonStock = safeParser.parse(balance.getCommonStock());
            this.retainedEarnings = safeParser.parse(balance.getRetainedEarnings());
            this.treasuryStock = safeParser.parse(balance.getTreasuryStock());

            // Cash Flow Statement
            this.operatingCashflow = safeParser.parse(cashFlow.getOperatingCashFlow());
            this.capitalExpenditures = safeParser.parse(cashFlow.getCapitalExpenditure());
            this.dividendPayout = safeParser.parse(cashFlow.getNetDividendsPaid());
            this.dividendPayoutCommonStock = safeParser.parse(cashFlow.getCommonDividendsPaid());
            this.dividendPayoutPreferredStock = safeParser.parse(cashFlow.getPreferredDividendsPaid());
            this.stockBasedCompensation = safeParser.parse(cashFlow.getStockBasedCompensation());
            this.commonStockRepurchased = safeParser.parse(cashFlow.getCommonStockRepurchased());

            // Shares Outstanding
            this.sharesOutstanding = safeParser.parse(income.getWeightedAverageShsOutDil());
            this.sharesOutstandingBasic = safeParser.parse(income.getWeightedAverageShsOut());
        }

        ParsedFinancialData build() {
            return new ParsedFinancialData(this);
        }
    }
}
