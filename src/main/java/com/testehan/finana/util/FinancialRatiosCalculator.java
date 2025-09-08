package com.testehan.finana.util;

import com.testehan.finana.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FinancialRatiosCalculator {

    private final SafeParser safeParser = new SafeParser();

    public FinancialRatiosReport calculateRatios(IncomeReport incomeReport,
                                                 BalanceSheetReport balanceSheetReport,
                                                 CashFlowReport cashFlowReport,
                                                 SharesOutstandingReport sharesOutstandingReport) {
        FinancialRatiosReport ratios = new FinancialRatiosReport();
        ratios.setFiscalDateEnding(incomeReport.getDate());

        // Parse all values once
        ParsedFinancialData data = parseFinancialData(incomeReport, balanceSheetReport, cashFlowReport, sharesOutstandingReport);

        // Profitability Ratios
        calculateGrossProfitMargin(ratios, data);
        calculateNetProfitMargin(ratios, data);
        calculateOperatingProfitMargin(ratios, data);
        calculateEbitdaMargin(ratios, data);
        calculateReturnOnAssets(ratios, data);
        calculateReturnOnEquity(ratios, data);
        calculateRoic(ratios, data);

        // Liquidity Ratios
        calculateCurrentRatio(ratios, data);
        calculateQuickRatio(ratios, data);
        calculateCashRatio(ratios, data);

        // Leverage Ratios
        calculateDebtToAssetsRatio(ratios, data);
        calculateDebtToEquityRatio(ratios, data);
        calculateInterestCoverageRatio(ratios, data);
        calculateNetDebtToEbitda(ratios, data);
        calculateDebtServiceCoverageRatio(ratios, data);

        // Efficiency Ratios
        calculateAssetTurnover(ratios, data);
        calculateInventoryTurnover(ratios, data);
        calculateReceivablesTurnover(ratios, data);
        calculatePayablesTurnover(ratios, data);
        calculateDaysSalesOutstanding(ratios, data);
        calculateDaysInventoryOutstanding(ratios, data);
        calculateDaysPayablesOutstanding(ratios, data);
        calculateCashConversionCycle(ratios, data);

        // Cash Flow Metrics
        calculateFreeCashFlow(ratios, data);
        calculateOperatingCashFlowRatio(ratios, data);
        calculateCashFlowToDebtRatio(ratios, data);

        // Per Share Metrics
        calculateEarningsPerShareBasic(ratios, data);
        calculateEarningsPerShareDiluted(ratios, data);
        calculateBookValuePerShare(ratios, data);
        calculateTangibleBookValuePerShare(ratios, data);
        calculateSalesPerShare(ratios, data);
        calculateFreeCashFlowPerShare(ratios, data);
        calculateOperatingCashFlowPerShare(ratios, data);
        calculateCashPerShare(ratios, data);

        // Dividend Metrics
        calculateDividendPerShare(ratios, data);
        calculateDividendYield(ratios, data);
        calculateDividendPayoutRatio(ratios, data);
        calculateBuybackYield(ratios, data);

        // Other Metrics
        calculateWorkingCapital(ratios, data);
        calculateAltmanZScore(ratios, data);

        return ratios;
    }

    // ==================== DATA PARSING ====================

    private ParsedFinancialData parseFinancialData(IncomeReport income,
                                                   BalanceSheetReport balance,
                                                   CashFlowReport cashFlow,
                                                   SharesOutstandingReport shares) {
        ParsedFinancialData data = new ParsedFinancialData();

        // Income Statement
        data.totalRevenue = safeParser.parse(income.getRevenue());
        data.grossProfit = safeParser.parse(income.getGrossProfit());
        data.costOfRevenue = safeParser.parse(income.getCostOfRevenue());
        data.netIncome = safeParser.parse(income.getNetIncome());
        data.ebit = safeParser.parse(income.getEbit());
        data.ebitda = safeParser.parse(income.getEbitda());
        data.incomeTaxExpense = safeParser.parse(income.getIncomeTaxExpense());
        data.incomeBeforeTax = safeParser.parse(income.getIncomeBeforeTax());
        data.interestExpense = safeParser.parse(income.getInterestExpense());

        // Balance Sheet - Assets (Current)
        data.totalAssets = safeParser.parse(balance.getTotalAssets());
        data.totalCurrentAssets = safeParser.parse(balance.getTotalCurrentAssets());
        data.cash = safeParser.parse(balance.getCashAndShortTermInvestments());
        data.shortTermInvestments = safeParser.parse(balance.getShortTermInvestments());
        data.netReceivables = safeParser.parse(balance.getNetReceivables());
        data.inventory = safeParser.parse(balance.getInventory());
        data.otherCurrentAssets = safeParser.parse(balance.getOtherCurrentAssets());

        // Balance Sheet - Assets (Non-Current)
        data.propertyPlantEquipment = safeParser.parse(balance.getPropertyPlantEquipmentNet());
        data.goodwill = safeParser.parse(balance.getGoodwill());
        data.intangibleAssets = safeParser.parse(balance.getIntangibleAssets());
        data.longTermInvestments = safeParser.parse(balance.getLongTermInvestments());
        data.otherNonCurrentAssets = safeParser.parse(balance.getOtherNonCurrentAssets());

        // Balance Sheet - Liabilities (Current)
        data.totalLiabilities = safeParser.parse(balance.getTotalLiabilities());
        data.currentAccountsPayable = safeParser.parse(balance.getAccountPayables());
        data.deferredRevenue = safeParser.parse(balance.getDeferredRevenue());
        data.shortTermDebt = safeParser.parse(balance.getShortTermDebt());
        data.otherCurrentLiabilities = safeParser.parse(balance.getOtherCurrentLiabilities());

        // Balance Sheet - Liabilities (Non-Current)
        data.longTermDebt = safeParser.parse(balance.getLongTermDebt());
        data.otherNonCurrentLiabilities = safeParser.parse(balance.getOtherNonCurrentLiabilities());
        data.capitalLeaseObligations = safeParser.parse(balance.getCapitalLeaseObligations());

        // Balance Sheet - Equity
        data.totalShareholderEquity = safeParser.parse(balance.getTotalStockholdersEquity());
        data.commonStock = safeParser.parse(balance.getCommonStock());
        data.retainedEarnings = safeParser.parse(balance.getRetainedEarnings());
        data.treasuryStock = safeParser.parse(balance.getTreasuryStock());


        // Cash Flow Statement
        data.operatingCashflow = safeParser.parse(cashFlow.getOperatingCashFlow());
        data.capitalExpenditures = safeParser.parse(cashFlow.getCapitalExpenditure());
        data.dividendPayout = safeParser.parse(cashFlow.getNetDividendsPaid());
        data.dividendPayoutCommonStock = safeParser.parse(cashFlow.getCommonDividendsPaid());
        data.dividendPayoutPreferredStock = safeParser.parse(cashFlow.getPreferredDividendsPaid());

        // Shares Outstanding
        data.sharesOutstanding = safeParser.parse(shares.getSharesOutstandingDiluted());
        data.sharesOutstandingBasic = safeParser.parse(shares.getSharesOutstandingBasic());

        // Calculate composite values
        data.totalDebt = data.shortTermDebt.add(data.longTermDebt);
        data.netDebt = data.totalDebt.subtract(data.cash);
        data.currentLiabilities = data.shortTermDebt
                .add(data.currentAccountsPayable)
                .add(data.otherCurrentLiabilities)
                .add(data.deferredRevenue);
        data.quickAssets = data.totalCurrentAssets.subtract(data.inventory);
        data.tangibleEquity = data.totalShareholderEquity.subtract(data.goodwill).subtract(data.intangibleAssets);

        return data;
    }

    // ==================== PROFITABILITY RATIOS ====================

    private void calculateGrossProfitMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setGrossProfitMargin(data.grossProfit.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateNetProfitMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setNetProfitMargin(data.netIncome.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateOperatingProfitMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setOperatingProfitMargin(data.ebit.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateEbitdaMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setEbitdaMargin(data.ebitda.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateReturnOnAssets(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setReturnOnAssets(data.netIncome.divide(data.totalAssets, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateReturnOnEquity(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalShareholderEquity.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setReturnOnEquity(data.netIncome.divide(data.totalShareholderEquity, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateRoic(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Tax Rate
        BigDecimal taxRate = BigDecimal.ZERO;
        if (data.incomeBeforeTax != null && data.incomeBeforeTax.compareTo(BigDecimal.ZERO) > 0) {
            taxRate = data.incomeTaxExpense.divide(data.incomeBeforeTax, 4, RoundingMode.HALF_UP);
        }

        // 2. NOPAT (Net Operating Profit After Tax)
        BigDecimal nopat = data.ebit.multiply(BigDecimal.ONE.subtract(taxRate));

        // 3. Invested Capital (Standard Approach)
        // Formula: Total Equity + Total Debt + Lease Liabilities
        // DO NOT subtract cash if you want to match standard market data for Tech giants.

        // Note: Alpha Vantage 'totalDebt' usually includes Short+Long term debt.
        // But check if your data object has 'capitalLeaseObligations' or similar.
        // If not, just Debt + Equity is a safe 90% solution.

        BigDecimal investedCapital = data.totalShareholderEquity.add(data.totalDebt);

        // Optional: Add Capital Lease Obligations if you have that field mapped
        if (data.capitalLeaseObligations != null) {
            investedCapital = investedCapital.add(data.capitalLeaseObligations);
        }

        // 4. Final Calculation
        if (investedCapital.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setRoic(nopat.divide(investedCapital, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setRoic(null);
        }
    }

    // ==================== LIQUIDITY RATIOS ====================

    private void calculateCurrentRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setCurrentRatio(data.totalCurrentAssets.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateQuickRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setQuickRatio(data.quickAssets.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateCashRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setCashRatio(data.cash.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
        }
    }

    // ==================== LEVERAGE RATIOS ====================

    private void calculateDebtToAssetsRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDebtToAssetsRatio(data.totalLiabilities.divide(data.totalAssets, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateDebtToEquityRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalShareholderEquity.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDebtToEquityRatio(data.totalLiabilities.divide(data.totalShareholderEquity, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateInterestCoverageRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.interestExpense.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setInterestCoverageRatio(data.ebit.divide(data.interestExpense, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateNetDebtToEbitda(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.ebitda.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setNetDebtToEbitda(data.netDebt.divide(data.ebitda, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateDebtServiceCoverageRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // DSCR = Net Operating Income / Total Debt Service
        // Using EBITDA as proxy for Net Operating Income
        // Total Debt Service = Principal + Interest (approximating with interest expense)
        if (data.interestExpense.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDebtServiceCoverageRatio(data.ebitda.divide(data.interestExpense, 4, RoundingMode.HALF_UP));
        }
    }

    // ==================== EFFICIENCY RATIOS ====================

    private void calculateAssetTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setAssetTurnover(data.totalRevenue.divide(data.totalAssets, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateInventoryTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.inventory.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setInventoryTurnover(data.costOfRevenue.divide(data.inventory, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateReceivablesTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.netReceivables.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setReceivablesTurnover(data.totalRevenue.divide(data.netReceivables, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculatePayablesTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.currentAccountsPayable.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setPayablesTurnover(data.costOfRevenue.divide(data.currentAccountsPayable, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateDaysSalesOutstanding(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal receivablesTurnover = ratios.getReceivablesTurnover();
        if (receivablesTurnover != null && receivablesTurnover.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDaysSalesOutstanding(new BigDecimal("365").divide(receivablesTurnover, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateDaysInventoryOutstanding(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal inventoryTurnover = ratios.getInventoryTurnover();
        if (inventoryTurnover != null && inventoryTurnover.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDaysInventoryOutstanding(new BigDecimal("365").divide(inventoryTurnover, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateDaysPayablesOutstanding(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal payablesTurnover = ratios.getPayablesTurnover();
        if (payablesTurnover != null && payablesTurnover.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDaysPayablesOutstanding(new BigDecimal("365").divide(payablesTurnover, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateCashConversionCycle(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal dso = ratios.getDaysSalesOutstanding();
        BigDecimal dio = ratios.getDaysInventoryOutstanding();
        BigDecimal dpo = ratios.getDaysPayablesOutstanding();

        if (dso != null && dio != null && dpo != null) {
            ratios.setCashConversionCycle(dio.add(dso).subtract(dpo));
        }
    }

    // ==================== CASH FLOW METRICS ====================

    private void calculateFreeCashFlow(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal fcf = data.operatingCashflow.subtract(data.capitalExpenditures);
        ratios.setFreeCashFlow(fcf);
    }

    private void calculateOperatingCashFlowRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setOperatingCashFlowRatio(data.operatingCashflow.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateCashFlowToDebtRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalDebt.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setCashFlowToDebtRatio(data.operatingCashflow.divide(data.totalDebt, 4, RoundingMode.HALF_UP));
        }
    }

    // ==================== PER SHARE METRICS ====================

    private void calculateEarningsPerShareBasic(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal shares = data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0
                ? data.sharesOutstandingBasic
                : data.sharesOutstanding;

        if (shares.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setEarningsPerShareBasic(data.netIncome.divide(shares, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateEarningsPerShareDiluted(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setEarningsPerShareDiluted(data.netIncome.divide(data.sharesOutstanding, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateBookValuePerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setBookValuePerShare(data.totalShareholderEquity.divide(data.sharesOutstanding, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateTangibleBookValuePerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setTangibleBookValuePerShare(data.tangibleEquity.divide(data.sharesOutstanding, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateSalesPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setSalesPerShare(data.totalRevenue.divide(data.sharesOutstanding, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateFreeCashFlowPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal fcf = ratios.getFreeCashFlow();
        if (fcf != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setFreeCashFlowPerShare(fcf.divide(data.sharesOutstanding, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateOperatingCashFlowPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setOperatingCashFlowPerShare(data.operatingCashflow.divide(data.sharesOutstanding, 4, RoundingMode.HALF_UP));
        }
    }

    private void calculateCashPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setCashPerShare(data.cash.divide(data.sharesOutstanding, 4, RoundingMode.HALF_UP));
        }
    }

    // ==================== DIVIDEND METRICS ====================

    private void calculateDividendPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // Prefer common stock dividends, fall back to total dividend payout
        BigDecimal dividends = data.dividendPayoutCommonStock.compareTo(BigDecimal.ZERO) > 0
                ? data.dividendPayoutCommonStock
                : data.dividendPayout;

        if (data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0 && dividends.compareTo(BigDecimal.ZERO) > 0) {
            // Note: dividend payout is typically negative in cash flow statement
            ratios.setDividendPerShare(dividends.abs().divide(data.sharesOutstanding, 4, RoundingMode.HALF_UP));
        }
    }

    // TODO
    private void calculateDividendYield(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // Would require stock price - set to null if not available
        ratios.setDividendYield(null);
    }

    private void calculateDividendPayoutRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal dividends = data.dividendPayoutCommonStock.compareTo(BigDecimal.ZERO) > 0
                ? data.dividendPayoutCommonStock
                : data.dividendPayout;

        if (data.netIncome.compareTo(BigDecimal.ZERO) > 0 && dividends.compareTo(BigDecimal.ZERO) != 0) {
            ratios.setDividendPayoutRatio(dividends.abs().divide(data.netIncome, 4, RoundingMode.HALF_UP));
        }
    }

    // TODO
    private void calculateBuybackYield(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // Would require stock price and buyback data - set to null if not available
        ratios.setBuybackYield(null);
    }

    // ==================== OTHER METRICS ====================

    private void calculateWorkingCapital(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal workingCapital = data.totalCurrentAssets.subtract(data.currentLiabilities);
        ratios.setWorkingCapital(workingCapital);
    }

    private void calculateAltmanZScore(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // Altman Z-Score = 1.2*X1 + 1.4*X2 + 3.3*X3 + 0.6*X4 + 1.0*X5
        // X1 = Working Capital / Total Assets
        // X2 = Retained Earnings / Total Assets
        // X3 = EBIT / Total Assets
        // X4 = Market Value of Equity / Total Liabilities
        // X5 = Sales / Total Assets

        if (data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal workingCapital = data.totalCurrentAssets.subtract(data.currentLiabilities);
            BigDecimal x1 = workingCapital.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

            // X2: Using retained earnings from balance sheet (now available)
            BigDecimal x2 = data.retainedEarnings.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

            BigDecimal x3 = data.ebit.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

            // X4: Would need market cap (requires stock price)
            // Using book value of equity as proxy
            BigDecimal x4 = BigDecimal.ZERO;
            if (data.totalLiabilities.compareTo(BigDecimal.ZERO) > 0) {
                x4 = data.totalShareholderEquity.divide(data.totalLiabilities, 4, RoundingMode.HALF_UP);
            }

            BigDecimal x5 = data.totalRevenue.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

            BigDecimal zScore = x1.multiply(new BigDecimal("1.2"))
                    .add(x2.multiply(new BigDecimal("1.4")))
                    .add(x3.multiply(new BigDecimal("3.3")))
                    .add(x4.multiply(new BigDecimal("0.6")))
                    .add(x5.multiply(new BigDecimal("1.0")));

            ratios.setAltmanZScore(zScore);
        }
    }

    // ==================== DATA CLASS ====================

    private static class ParsedFinancialData {
        // Income Statement
        BigDecimal totalRevenue;
        BigDecimal grossProfit;
        BigDecimal costOfRevenue;
        BigDecimal netIncome;
        BigDecimal ebit;
        BigDecimal ebitda;
        BigDecimal incomeTaxExpense;
        BigDecimal incomeBeforeTax;
        BigDecimal interestExpense;

        // Balance Sheet - Assets (Current)
        BigDecimal totalAssets;
        BigDecimal totalCurrentAssets;
        BigDecimal cash;
        BigDecimal shortTermInvestments;
        BigDecimal netReceivables;
        BigDecimal inventory;
        BigDecimal otherCurrentAssets;

        // Balance Sheet - Assets (Non-Current)
        BigDecimal propertyPlantEquipment;
        BigDecimal goodwill;
        BigDecimal intangibleAssets;
        BigDecimal longTermInvestments;
        BigDecimal otherNonCurrentAssets;

        // Balance Sheet - Liabilities (Current)
        BigDecimal totalLiabilities;
        BigDecimal currentAccountsPayable;
        BigDecimal deferredRevenue;
        BigDecimal shortTermDebt;
        BigDecimal otherCurrentLiabilities;

        // Balance Sheet - Liabilities (Non-Current)
        BigDecimal longTermDebt;
        BigDecimal otherNonCurrentLiabilities;
        BigDecimal capitalLeaseObligations;

        // Balance Sheet - Equity
        BigDecimal totalShareholderEquity;
        BigDecimal commonStock;
        BigDecimal retainedEarnings;
        BigDecimal treasuryStock;
        BigDecimal accumulatedOtherComprehensiveIncome;

        // Cash Flow Statement
        BigDecimal operatingCashflow;
        BigDecimal capitalExpenditures;
        BigDecimal dividendPayout;
        BigDecimal dividendPayoutCommonStock;
        BigDecimal dividendPayoutPreferredStock;

        // Shares
        BigDecimal sharesOutstanding;
        BigDecimal sharesOutstandingBasic;

        // Computed values
        BigDecimal totalDebt;
        BigDecimal netDebt;
        BigDecimal currentLiabilities;
        BigDecimal quickAssets;
        BigDecimal tangibleEquity;
    }
}
