package com.testehan.finana.util;

import com.testehan.finana.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FinancialRatiosCalculator {

    private final SafeParser safeParser = new SafeParser();
// TODO if you compare some of the numbers resulted from the calculator with other apps, you will see differences...so
    // maybe there are some mistakes... you can get key metrics and ratios directly from FMP but it is only anual data..
    // i think i sould keep this calculator, maybe fixed it add tests..since i can use it to calculate quartely data as well
    // on the paid version..you need to pay $49 per month for this..

    public FinancialRatiosReport calculateRatios(CompanyOverview companyOverview,
                                                 IncomeReport incomeReport,
                                                 BalanceSheetReport balanceSheetReport,
                                                 CashFlowReport cashFlowReport) {
        FinancialRatiosReport ratios = new FinancialRatiosReport();
        ratios.setDate(incomeReport.getDate());

        // Parse all values once
        ParsedFinancialData data = parseFinancialData(companyOverview, incomeReport, balanceSheetReport, cashFlowReport);

        // Profitability Ratios
        calculateGrossProfitMargin(ratios, data);
        calculateNetProfitMargin(ratios, data);
        calculateOperatingProfitMargin(ratios, data);
        calculateEbitdaMargin(ratios, data);
        calculateAdjustedEbitdaMargin(ratios,data);
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
        calculateSalesToCapitalRatio(ratios, data);

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
        calculateFcfMargin(ratios, data);
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

    private ParsedFinancialData parseFinancialData(CompanyOverview companyOverview,
                                                   IncomeReport income,
                                                   BalanceSheetReport balance,
                                                   CashFlowReport cashFlow) {
        ParsedFinancialData data = new ParsedFinancialData();

        data.marketCap = safeParser.parse(companyOverview.getMarketCap());

        // Income Statement
        data.totalRevenue = safeParser.parse(income.getRevenue());
        data.grossProfit = safeParser.parse(income.getGrossProfit());
        data.costOfRevenue = safeParser.parse(income.getCostOfRevenue());
        data.operatingIncome = safeParser.parse(income.getOperatingIncome());
        data.netIncome = safeParser.parse(income.getNetIncome());
        data.ebit = safeParser.parse(income.getEbit());
        data.ebitda = safeParser.parse(income.getEbitda());
        data.incomeTaxExpense = safeParser.parse(income.getIncomeTaxExpense());
        data.incomeBeforeTax = safeParser.parse(income.getIncomeBeforeTax());
        data.interestExpense = safeParser.parse(income.getInterestExpense());
        data.eps = safeParser.parse(income.getEps());
        data.epsDiluted = safeParser.parse(income.getEpsDiluted());

        // Balance Sheet - Assets (Current)
        data.totalAssets = safeParser.parse(balance.getTotalAssets());
        data.totalCurrentAssets = safeParser.parse(balance.getTotalCurrentAssets());
        data.cash = safeParser.parse(balance.getCashAndCashEquivalents());
        data.shortTermInvestments = safeParser.parse(balance.getShortTermInvestments());
        data.netReceivables = safeParser.parse(balance.getNetReceivables());
        data.inventory = safeParser.parse(balance.getInventory());
        data.otherCurrentAssets = safeParser.parse(balance.getOtherCurrentAssets());
        data.minorityInterest = safeParser.parse(balance.getMinorityInterest());

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
        data.stockBasedCompensation = safeParser.parse(cashFlow.getStockBasedCompensation());

        // Shares Outstanding
        data.sharesOutstanding = safeParser.parse(income.getWeightedAverageShsOutDil());
        data.sharesOutstandingBasic = safeParser.parse(income.getWeightedAverageShsOut());

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
        // Check for NULLs first to avoid crashing
        if (data.totalRevenue != null && data.grossProfit != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0)
        {
            ratios.setGrossProfitMargin(data.grossProfit.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        } else {
            // Handle Banks or missing data
            ratios.setGrossProfitMargin(null);
        }
    }

    private void calculateNetProfitMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure neither Revenue nor NetIncome is null
        if (data.totalRevenue != null && data.netIncome != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0)
        {

            ratios.setNetProfitMargin(data.netIncome.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setNetProfitMargin(null);
        }
    }

    private void calculateOperatingProfitMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure Revenue exists and is not zero
        if (data.totalRevenue != null && data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Select the best numerator
            // Prefer 'operatingIncome' for true Operating Margin.
            // Fall back to 'ebit' only if operatingIncome is missing.
            BigDecimal numerator = (data.operatingIncome != null) ? data.operatingIncome : data.ebit;

            // 3. Final Safety Check on Numerator
            if (numerator != null) {
                ratios.setOperatingProfitMargin(numerator.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
            } else {
                ratios.setOperatingProfitMargin(null);
            }
        } else {
            ratios.setOperatingProfitMargin(null);
        }
    }

    private void calculateEbitdaMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists
        if (data.totalRevenue != null && data.ebitda != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0)
        {
            ratios.setEbitdaMargin(data.ebitda.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setEbitdaMargin(null);
        }
    }

    private void calculateAdjustedEbitdaMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Need Revenue and EBITDA to start
        if (data.totalRevenue != null && data.ebitda != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Start with standard EBITDA
            BigDecimal adjustedEbitda = data.ebitda;

            // 3. Add back Stock Based Compensation (The #1 Adjustment)
            // Note: FMP usually puts this in the Cash Flow statement part of the response
            if (data.stockBasedCompensation != null) {
                adjustedEbitda = adjustedEbitda.add(data.stockBasedCompensation);
            }

            // 5. Final Calculation
            ratios.setAdjustedEbitdaMargin(adjustedEbitda.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));

        } else {
            ratios.setAdjustedEbitdaMargin(null);
        }
    }

    private void calculateReturnOnAssets(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists (Prevent NullPointerException)
        if (data.totalAssets != null && data.netIncome != null &&
                data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate
            ratios.setReturnOnAssets(data.netIncome.divide(data.totalAssets, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setReturnOnAssets(null);
        }
    }

    private void calculateReturnOnEquity(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Equity is positive
        if (data.totalShareholderEquity != null && data.netIncome != null &&
                data.totalShareholderEquity.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate
            ratios.setReturnOnEquity(data.netIncome.divide(data.totalShareholderEquity, 4, RoundingMode.HALF_UP));
        } else {
            // Returns null if data is missing OR if Equity is negative
            ratios.setReturnOnEquity(null);
        }
    }

    public void calculateRoic(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // --- 1. Calculate Tax Rate (With Safety Guards) ---
        BigDecimal taxRate;
        BigDecimal incomeBeforeTax = data.incomeBeforeTax;
        BigDecimal taxExpense = data.incomeTaxExpense;

        // Guard: Use a standard rate (e.g., 21%) if data is weird,
        // or if ETR is negative/unrealistically high.
        // Many platforms default to 21% or 25% to normalize.
        if (incomeBeforeTax != null && incomeBeforeTax.compareTo(BigDecimal.ZERO) > 0) {
            taxRate = taxExpense.divide(incomeBeforeTax, 4, RoundingMode.HALF_UP);

            // Clamp tax rate between 0% and 35% to prevent one-off distortions
            if (taxRate.compareTo(BigDecimal.ZERO) < 0) taxRate = new BigDecimal("0.21");
            if (taxRate.compareTo(new BigDecimal("0.35")) > 0) taxRate = new BigDecimal("0.21");
        } else {
            // Fallback standard corporate tax rate
            taxRate = new BigDecimal("0.21");
        }

        // --- 2. Calculate NOPAT ---
        // Use Operating Income if available, otherwise EBIT.
        // Operating Income is purer for ROIC.
        BigDecimal operatingIncome = (data.operatingIncome != null) ? data.operatingIncome : data.ebit;
        BigDecimal nopat = operatingIncome.multiply(BigDecimal.ONE.subtract(taxRate));

        // --- 3. Calculate Invested Capital (Financing Approach) ---
        // Formula: (Equity + Total Debt + Leases + Minority Interest) - Cash & Equivalents

        BigDecimal investedCapital = BigDecimal.ZERO;

        // Add Equity
        investedCapital = investedCapital.add(data.totalShareholderEquity != null ? data.totalShareholderEquity : BigDecimal.ZERO);

        // Add Total Debt (Short + Long Term)
        investedCapital = investedCapital.add(data.totalDebt != null ? data.totalDebt : BigDecimal.ZERO);

        // Add Capital Lease Obligations (FMP usually separates this from Total Debt)
        if (data.capitalLeaseObligations != null) {
            investedCapital = investedCapital.add(data.capitalLeaseObligations);
        }

        // Add Minority Interest (CRITICAL: Often missed. FMP usually has this field)
        if (data.minorityInterest != null) {
            investedCapital = investedCapital.add(data.minorityInterest);
        }

        // SUBTRACT Cash (The Excess Cash Adjustment)
        // Most formulas subtract Cash & Cash Equivalents + Short Term Investments
        if (data.cash != null) {
            investedCapital = investedCapital.subtract(data.cash);
        }
        if (data.shortTermInvestments != null) {
            investedCapital = investedCapital.subtract(data.shortTermInvestments);
        }

        // --- 4. Final Calculation ---
        // Note: If you have access to historical data, strictly you should use:
        // NOPAT / ((InvestedCapital_Current + InvestedCapital_LastYear) / 2)

        if (investedCapital.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setRoic(nopat.divide(investedCapital, 4, RoundingMode.HALF_UP));
        } else if (nopat.compareTo(BigDecimal.ZERO) > 0) {
            // If Capital is negative but Profit is positive, this is an Infinite Moat
            // Set to a capped high number (e.g., 1000%)
            ratios.setRoic(new BigDecimal("9.9999")); // Represents 999% or "Infinite"
        } else {
            // If Capital is negative AND Profit is negative, the company is just bankrupt/dying.
            ratios.setRoic(BigDecimal.ZERO);
        }
    }

    // ==================== LIQUIDITY RATIOS ====================

    private void calculateCurrentRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Liabilities > 0
        if (data.totalCurrentAssets != null && data.currentLiabilities != null &&
                data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate
            ratios.setCurrentRatio(data.totalCurrentAssets.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setCurrentRatio(null);
        }
    }

    private void calculateQuickRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Liabilities must exist and be positive
        if (data.currentLiabilities != null && data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal numerator = null;

            // 2. Determine the Numerator (Quick Assets)
            if (data.quickAssets != null) {
                // Use the field if you have it mapped from the API
                numerator = data.quickAssets;
            }
            else if (data.totalCurrentAssets != null) {
                // Fallback: Calculate it manually (Standard Approach)
                // Formula: Current Assets - Inventory
                numerator = data.totalCurrentAssets;

                if (data.inventory != null) {
                    numerator = numerator.subtract(data.inventory);
                }
            }

            // 3. Final Calculation
            if (numerator != null) {
                ratios.setQuickRatio(
                        numerator.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP)
                );
            } else {
                ratios.setQuickRatio(null);
            }

        } else {
            ratios.setQuickRatio(null);
        }
    }

    private void calculateCashRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure Liabilities exists and is > 0
        if (data.currentLiabilities != null && data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Safety Check: Ensure your mapped 'cash' variable isn't null
            if (data.cash != null) {
                ratios.setCashRatio(data.cash.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
            } else {
                ratios.setCashRatio(null);
            }

        } else {
            ratios.setCashRatio(null);
        }
    }

    // ==================== LEVERAGE RATIOS ====================

    private void calculateDebtToAssetsRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Assets are positive
        if (data.totalAssets != null && data.totalLiabilities != null &&
                data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate (Total Liabilities / Total Assets)
            ratios.setDebtToAssetsRatio(data.totalLiabilities.divide(data.totalAssets, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDebtToAssetsRatio(null);
        }
    }

    private void calculateDebtToEquityRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Equity is positive
        if (data.totalShareholderEquity != null && data.totalDebt != null &&
                data.totalShareholderEquity.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate: (Short Term Debt + Long Term Debt) / Equity
            ratios.setDebtToEquityRatio(data.totalDebt.divide(data.totalShareholderEquity, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDebtToEquityRatio(null);
        }
    }

    private void calculateInterestCoverageRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists
        if (data.ebit != null && data.interestExpense != null) {

            // 2. Normalize Interest Expense (Get Absolute Value)
            // This handles cases where API returns expense as negative (e.g. -100)
            BigDecimal interestAbs = data.interestExpense.abs();

            // 3. Prevent Divide by Zero
            if (interestAbs.compareTo(BigDecimal.ZERO) > 0) {
                ratios.setInterestCoverageRatio(data.ebit.divide(interestAbs, 4, RoundingMode.HALF_UP));
            } else {
                // Case: Company has NO debt/interest.
                // Technically coverage is "Infinite". Returning null is safe.
                ratios.setInterestCoverageRatio(null);
            }
        } else {
            ratios.setInterestCoverageRatio(null);
        }
    }

    private void calculateNetDebtToEbitda(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists
        if (data.netDebt != null && data.ebitda != null &&
                data.ebitda.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate
            ratios.setNetDebtToEbitda(data.netDebt.divide(data.ebitda, 4, RoundingMode.HALF_UP));
        } else {
            // Returns null if EBITDA is negative (company is losing money)
            // or if data is missing.
            ratios.setNetDebtToEbitda(null);
        }
    }

    private void calculateDebtServiceCoverageRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // Safety Checks
        if (data.ebitda != null && data.interestExpense != null) {

            // 1. Calculate Total Debt Service (Interest + Principal due soon)
            BigDecimal interest = data.interestExpense.abs();
            BigDecimal principal = (data.shortTermDebt != null) ? data.shortTermDebt : BigDecimal.ZERO;

            BigDecimal totalDebtService = interest.add(principal);

            // 2. Calculate Ratio
            if (totalDebtService.compareTo(BigDecimal.ZERO) > 0) {
                ratios.setDebtServiceCoverageRatio(data.ebitda.divide(totalDebtService, 4, RoundingMode.HALF_UP));
            } else {
                ratios.setDebtServiceCoverageRatio(null);
            }
        } else {
            ratios.setDebtServiceCoverageRatio(null);
        }
    }

    // ==================== EFFICIENCY RATIOS ====================

    private void calculateAssetTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Assets are positive
        if (data.totalRevenue != null && data.totalAssets != null &&
                data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate
            ratios.setAssetTurnover(data.totalRevenue.divide(data.totalAssets, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setAssetTurnover(null);
        }
    }

    private void calculateInventoryTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Inventory is positive
        // This effectively filters out Service companies, Banks, and SaaS
        if (data.costOfRevenue != null && data.inventory != null &&
                data.inventory.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate: COGS / Inventory
            ratios.setInventoryTurnover(data.costOfRevenue.divide(data.inventory, 4, RoundingMode.HALF_UP));
        } else {
            // Correct behavior for Service/Tech companies: Result is null (N/A)
            ratios.setInventoryTurnover(null);
        }
    }

    private void calculateReceivablesTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Receivables are positive
        if (data.totalRevenue != null && data.netReceivables != null &&
                data.netReceivables.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate
            ratios.setReceivablesTurnover(
                    data.totalRevenue.divide(data.netReceivables, 4, RoundingMode.HALF_UP)
            );
        } else {
            ratios.setReceivablesTurnover(null);
        }
    }

    private void calculatePayablesTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Payables are positive
        if (data.costOfRevenue != null && data.currentAccountsPayable != null &&
                data.currentAccountsPayable.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate: COGS / Accounts Payable
            ratios.setPayablesTurnover(data.costOfRevenue.divide(data.currentAccountsPayable, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setPayablesTurnover(null);
        }
    }

    private void calculateDaysSalesOutstanding(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // Formula: (Net Receivables / Total Revenue) * 365

        // 1. Safety Check: Revenue must exist and be > 0
        if (data.totalRevenue != null && data.netReceivables != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal daysInYear = new BigDecimal("365");

            // 2. Calculate
            // We multiply first to maintain precision before dividing
            BigDecimal dso = data.netReceivables.multiply(daysInYear).divide(data.totalRevenue, 4, RoundingMode.HALF_UP);

            ratios.setDaysSalesOutstanding(dso);
        } else {
            ratios.setDaysSalesOutstanding(null);
        }
    }

    private void calculateDaysInventoryOutstanding(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: COGS must exist and be > 0.
        // Note: We don't check if Inventory > 0 here because if it's 0, the result should just be 0 (or null), not a crash.
        if (data.costOfRevenue != null && data.inventory != null &&
                data.costOfRevenue.compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal daysInYear = new BigDecimal("365");

            // 2. Calculate: (Inventory * 365) / COGS
            // This is mathematically identical to 365 / Turnover
            BigDecimal numerator = data.inventory.multiply(daysInYear);

            ratios.setDaysInventoryOutstanding(numerator.divide(data.costOfRevenue, 4, RoundingMode.HALF_UP));

        } else {
            // Correct for Service/SaaS companies (No inventory = No DIO)
            ratios.setDaysInventoryOutstanding(null);
        }
    }

    private void calculateDaysPayablesOutstanding(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: COGS must exist and be > 0 (to divide by it)
        if (data.costOfRevenue != null && data.currentAccountsPayable != null &&
                data.costOfRevenue.compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal daysInYear = new BigDecimal("365");

            // 2. Calculate: (Accounts Payable * 365) / COGS
            // We multiply first to maintain precision
            BigDecimal numerator = data.currentAccountsPayable.multiply(daysInYear);

            ratios.setDaysPayablesOutstanding(numerator.divide(data.costOfRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDaysPayablesOutstanding(null);
        }
    }

    private void calculateCashConversionCycle(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal dso = ratios.getDaysSalesOutstanding();
        BigDecimal dpo = ratios.getDaysPayablesOutstanding();
        BigDecimal dio = ratios.getDaysInventoryOutstanding();

        // We strictly need DSO and DPO.
        // If a company doesn't have sales or payables, the metric is meaningless.
        if (dso != null && dpo != null) {

            // Handle Missing Inventory (Service/SaaS companies)
            // If DIO is null, treat it as 0.0 so we can still calculate CCC
            BigDecimal safeDio = (dio != null) ? dio : BigDecimal.ZERO;

            // Formula: DIO + DSO - DPO
            BigDecimal ccc = safeDio.add(dso).subtract(dpo);

            ratios.setCashConversionCycle(ccc);
        } else {
            ratios.setCashConversionCycle(null);
        }
    }

    private void calculateSalesToCapitalRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure all data exists
        // Note: We also need cashAndEquivalents now
        if (data.totalRevenue != null &&
                data.totalDebt != null &&
                data.totalShareholderEquity != null &&
                data.cash != null) {

            // 2. Calculate Invested Capital (The Damodaran Way)
            // Formula: Debt + Equity - Cash
            BigDecimal investedCapital = data.totalDebt
                    .add(data.totalShareholderEquity)
                    .subtract(data.cash)
                    .subtract(data.shortTermInvestments);

            // 3. Logic Check: Invested Capital must be positive for the ratio to make sense.
            // (Sometimes tech companies have more Cash than Debt+Equity, resulting in negative IC.
            // In that case, the ratio is mathematically undefined/meaningless).
            if (investedCapital.compareTo(BigDecimal.ZERO) > 0) {
                ratios.setSalesToCapitalRatio(
                        data.totalRevenue.divide(investedCapital, 4, RoundingMode.HALF_UP)
                );
            } else {
                // Handle negative invested capital (common in mature tech or cash-rich companies)
                // You might return null, 0, or a specific flag depending on your UI needs.
                ratios.setSalesToCapitalRatio(null);
            }

        } else {
            ratios.setSalesToCapitalRatio(null);
        }
    }

    // ==================== CASH FLOW METRICS ====================

    private void calculateFreeCashFlow(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists
        if (data.operatingCashflow != null && data.capitalExpenditures != null) {

            // 2. Normalize CapEx to be a positive magnitude
            // This handles external Data sevices returning either -100 or 100 safely.
            BigDecimal capex = data.capitalExpenditures.abs();

            // 3. Calculate: OCF - CapEx
            BigDecimal fcf = data.operatingCashflow.subtract(capex);

            ratios.setFreeCashFlow(fcf);
        } else {
            ratios.setFreeCashFlow(null);
        }
    }

    public void calculateFcfMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (ratios.getFreeCashFlow() != null && data.totalRevenue != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {

            ratios.setFreeCashflowMargin(ratios.getFreeCashFlow().divide(data.totalRevenue, 4, RoundingMode.HALF_UP)
            );
        }
    }

    private void calculateOperatingCashFlowRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Liabilities > 0
        if (data.operatingCashflow != null && data.currentLiabilities != null &&
                data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate
            ratios.setOperatingCashFlowRatio(data.operatingCashflow.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setOperatingCashFlowRatio(null);
        }
    }

    private void calculateCashFlowToDebtRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure data exists and Debt is positive
        if (data.operatingCashflow != null && data.totalDebt != null &&
                data.totalDebt.compareTo(BigDecimal.ZERO) > 0) {

            // 2. Calculate: Operating Cash Flow / Total Debt
            ratios.setCashFlowToDebtRatio(
                    data.operatingCashflow.divide(data.totalDebt, 4, RoundingMode.HALF_UP)
            );
        } else {
            // Returns null if data is missing OR if company has Zero Debt.
            // (Zero Debt is technically "Infinite" coverage, so null is appropriate)
            ratios.setCashFlowToDebtRatio(null);
        }
    }

    // ==================== PER SHARE METRICS ====================

    private void calculateEarningsPerShareBasic(FinancialRatiosReport ratios, ParsedFinancialData data) {
//        // 1. Safety Check: Net Income must exist
//        if (data.netIncome == null) {
//            ratios.setEarningsPerShareBasic(null);
//            return;
//        }
//
//        BigDecimal shares = null;
//
//        if (data.sharesOutstandingBasic != null && data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0) {
//            shares = data.sharesOutstandingBasic;
//        }
//        // Priority C: Instant Snapshot (Least accurate for historical data, but better than nothing)
//        else if (data.sharesOutstanding != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
//            shares = data.sharesOutstanding;
//        }
//
//        // 3. Final Calculation
//        if (shares != null) {
//            ratios.setEarningsPerShareBasic(data.netIncome.divide(shares, 4, RoundingMode.HALF_UP));
//        } else {
//            ratios.setEarningsPerShareBasic(null);
//        }

        // this comes from FMP api
        ratios.setEarningsPerShareBasic( data.eps );
    }

    private void calculateEarningsPerShareDiluted(FinancialRatiosReport ratios, ParsedFinancialData data) {
//        // 1. Safety Check
//        if (data.netIncome == null) {
//            ratios.setEarningsPerShareDiluted(null);
//            return;
//        }
//
//        // 2. Use your 'sharesOutstanding' variable (which you mapped to Diluted)
//        // Ideally, you should rename this variable to 'sharesOutstandingDiluted' to avoid confusion!
//        BigDecimal dilutedShares = data.sharesOutstanding;
//
//        if (dilutedShares != null && dilutedShares.compareTo(BigDecimal.ZERO) > 0) {
//            ratios.setEarningsPerShareDiluted(
//                    data.netIncome.divide(dilutedShares, 4, RoundingMode.HALF_UP)
//            );
//        } else {
//            // Fallback: If diluted is missing, basic is often used
//            ratios.setEarningsPerShareDiluted(ratios.getEarningsPerShareBasic());
//        }

        ratios.setEarningsPerShareDiluted(data.epsDiluted);
    }

    private void calculateBookValuePerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Equity must exist (Can be negative, so we just check null)
        if (data.totalShareholderEquity == null) {
            ratios.setBookValuePerShare(null);
            return;
        }

        // 2. Select the Correct Share Count
        // Standard BVPS uses Basic Shares, not Diluted.
        BigDecimal shares = null;

        if (data.sharesOutstandingBasic != null && data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstandingBasic;
        }
        // Fallback to diluted if basic is missing (unlikely, but safe)
        else if (data.sharesOutstanding != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstanding;
        }

        // 3. Calculate
        if (shares != null) {
            ratios.setBookValuePerShare(data.totalShareholderEquity.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setBookValuePerShare(null);
        }
    }

    private void calculateTangibleBookValuePerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Tangible Equity must exist (can be negative)
        if (data.tangibleEquity == null) {
            ratios.setTangibleBookValuePerShare(null);
            return;
        }

        // 2. Select the Correct Share Count (Basic Shares)
        BigDecimal shares = null;

        if (data.sharesOutstandingBasic != null && data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstandingBasic;
        }
        // Fallback only if basic is missing
        else if (data.sharesOutstanding != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstanding;
        }

        // 3. Calculate
        if (shares != null) {
            ratios.setTangibleBookValuePerShare(data.tangibleEquity.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setTangibleBookValuePerShare(null);
        }
    }

    private void calculateSalesPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Revenue must exist
        if (data.totalRevenue == null) {
            ratios.setSalesPerShare(null);
            return;
        }

        // 2. Select the Correct Share Count (Basic)
        BigDecimal shares = null;

        if (data.sharesOutstandingBasic != null && data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstandingBasic;
        }
        // Fallback if basic is missing
        else if (data.sharesOutstanding != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstanding;
        }

        // 3. Calculate
        if (shares != null) {
            ratios.setSalesPerShare(data.totalRevenue.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setSalesPerShare(null);
        }
    }

    private void calculateFreeCashFlowPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Cash Flow and CapEx must exist
        if (data.operatingCashflow != null && data.capitalExpenditures != null) {

            // 2. Re-calculate FCF locally (Safer than relying on getFreeCashFlow order)
            BigDecimal fcf = data.operatingCashflow.subtract(data.capitalExpenditures.abs());

            // 3. Select Share Count (Prefer Basic)
            BigDecimal shares = null;
            if (data.sharesOutstandingBasic != null && data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0) {
                shares = data.sharesOutstandingBasic;
            } else if (data.sharesOutstanding != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
                shares = data.sharesOutstanding;
            }

            // 4. Final Calculation
            if (shares != null) {
                ratios.setFreeCashFlowPerShare(fcf.divide(shares, 4, RoundingMode.HALF_UP));
            } else {
                ratios.setFreeCashFlowPerShare(null);
            }

        } else {
            ratios.setFreeCashFlowPerShare(null);
        }
    }

    private void calculateOperatingCashFlowPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: OCF must exist
        if (data.operatingCashflow == null) {
            ratios.setOperatingCashFlowPerShare(null);
            return;
        }

        // 2. Select Share Count: Prefer Basic Weighted Average
        BigDecimal shares = null;

        if (data.sharesOutstandingBasic != null && data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstandingBasic;
        }
        // Fallback to diluted if basic is missing
        else if (data.sharesOutstanding != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstanding;
        }

        // 3. Calculate
        if (shares != null) {
            ratios.setOperatingCashFlowPerShare(data.operatingCashflow.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setOperatingCashFlowPerShare(null);
        }
    }

    private void calculateCashPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Cash must exist
        // Note: Use your mapped 'cash' variable (which is Cash + Equivalents)
        if (data.cash == null) {
            ratios.setCashPerShare(null);
            return;
        }

        // 2. Select Share Count: Prefer Basic
        BigDecimal shares = null;

        if (data.sharesOutstandingBasic != null && data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstandingBasic;
        }
        // Fallback to diluted if basic is missing
        else if (data.sharesOutstanding != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstanding;
        }

        // 3. Calculate
        if (shares != null) {
            ratios.setCashPerShare(data.cash.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setCashPerShare(null);
        }
    }

    // ==================== DIVIDEND METRICS ====================

    private void calculateDividendPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Determine the Dividend Amount (Cash Flow is negative, so we need abs())
        BigDecimal totalDividendsPaid = BigDecimal.ZERO;

        // Priority 1: Common Stock Dividends (More accurate if available)
        if (data.dividendPayoutCommonStock != null &&
                data.dividendPayoutCommonStock.compareTo(BigDecimal.ZERO) != 0) {
            totalDividendsPaid = data.dividendPayoutCommonStock.abs();
        }
        // Priority 2: Total Dividends (Fallback)
        else if (data.dividendPayout != null) {
            totalDividendsPaid = data.dividendPayout.abs();
        }

        // 2. Select Share Count: MUST use Basic Shares
        // Dividends are not paid on unexercised options (Diluted).
        BigDecimal shares = null;

        if (data.sharesOutstandingBasic != null && data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstandingBasic;
        }
        // Fallback (only if basic is missing)
        else if (data.sharesOutstanding != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            shares = data.sharesOutstanding;
        }

        // 3. Final Calculation
        if (shares != null && totalDividendsPaid.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDividendPerShare(
                    totalDividendsPaid.divide(shares, 4, RoundingMode.HALF_UP)
            );
        } else {
            // Safe to return null or 0 if no dividends are paid
            ratios.setDividendPerShare(null);
        }
    }

    // TODO
    private void calculateDividendYield(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // Would require stock price - set to null if not available
        ratios.setDividendYield(null);
    }

    private void calculateDividendPayoutRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Determine Dividend Amount (Null-Safe & Sign-Aware)
        BigDecimal totalDividends = BigDecimal.ZERO;

        // Priority A: Specific Common Stock Dividends (Standard Definition)
        // Check != 0 because FMP returns negative numbers for outflows
        if (data.dividendPayoutCommonStock != null &&
                data.dividendPayoutCommonStock.compareTo(BigDecimal.ZERO) != 0) {

            totalDividends = data.dividendPayoutCommonStock.abs();
        }
        // Priority B: Total Dividends (Fallback)
        else if (data.dividendPayout != null) {
            totalDividends = data.dividendPayout.abs();
        }

        // 2. Safety Check: Net Income must exist and be Positive
        // Payout Ratio is meaningless if the company lost money (Negative Income)
        if (data.netIncome != null && data.netIncome.compareTo(BigDecimal.ZERO) > 0 &&
                totalDividends.compareTo(BigDecimal.ZERO) > 0) {

            // 3. Calculate: Dividends / Net Income
            ratios.setDividendPayoutRatio(totalDividends.divide(data.netIncome, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDividendPayoutRatio(null);
        }
    }

    // TODO
    private void calculateBuybackYield(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // Would require stock price and buyback data - set to null if not available
        ratios.setBuybackYield(null);
    }

    // ==================== OTHER METRICS ====================

    private void calculateWorkingCapital(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // 1. Safety Check: Ensure both fields exist
        if (data.totalCurrentAssets != null && data.currentLiabilities != null) {

            // 2. Calculate: Assets - Liabilities
            BigDecimal workingCapital = data.totalCurrentAssets.subtract(data.currentLiabilities);
            ratios.setWorkingCapital(workingCapital);

        } else {
            // Banks often return null for "Current" assets/liabilities.
            // Returning null is the correct behavior for them.
            ratios.setWorkingCapital(null);
        }
    }

    private void calculateAltmanZScore(FinancialRatiosReport ratios, ParsedFinancialData data) {
        // Altman Z-Score = 1.2*X1 + 1.4*X2 + 3.3*X3 + 0.6*X4 + 1.0*X5
        // X1 = Working Capital / Total Assets
        // X2 = Retained Earnings / Total Assets
        // X3 = EBIT / Total Assets
        // X4 = Market Value of Equity / Total Liabilities
        // X5 = Sales / Total Assets

        // 1. Safety Check: Total Assets must exist and be > 0
        if (data.totalAssets == null || data.totalAssets.compareTo(BigDecimal.ZERO) <= 0) {
            ratios.setAltmanZScore(null);
            return;
        }

        // 2. Safety Check: Ensure other required fields are not null
        // (We treat missing Retained Earnings as 0.0 to be conservative/safe)
        if (data.totalCurrentAssets == null || data.currentLiabilities == null ||
                data.ebit == null || data.totalRevenue == null || data.totalLiabilities == null) {
            ratios.setAltmanZScore(null);
            return;
        }

        // --- Calculate Components ---

        // X1: Working Capital / Total Assets
        // Weight: 1.2
        BigDecimal workingCapital = data.totalCurrentAssets.subtract(data.currentLiabilities);
        BigDecimal x1 = workingCapital.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

        // X2: Retained Earnings / Total Assets
        // Weight: 1.4
        // Safe handling: If Retained Earnings is null, assume 0 (common for new companies)
        BigDecimal retainedEarnings = (data.retainedEarnings != null) ? data.retainedEarnings : BigDecimal.ZERO;
        BigDecimal x2 = retainedEarnings.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

        // X3: EBIT / Total Assets
        // Weight: 3.3
        BigDecimal x3 = data.ebit.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

        // X4: Market Value of Equity / Total Liabilities
        // Weight: 0.6
        BigDecimal equityValue;

        // CRITICAL: Prefer Market Cap (Standard Formula). Fallback to Book Value (Modified).
        if (data.marketCap != null && data.marketCap.compareTo(BigDecimal.ZERO) > 0) {
            equityValue = data.marketCap;
        } else {
            // Warning: This makes the score significantly lower (more pessimistic)
            equityValue = (data.totalShareholderEquity != null) ? data.totalShareholderEquity : BigDecimal.ZERO;
        }

        BigDecimal x4 = BigDecimal.ZERO;
        if (data.totalLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            x4 = equityValue.divide(data.totalLiabilities, 4, RoundingMode.HALF_UP);
        }

        // X5: Sales / Total Assets
        // Weight: 1.0
        BigDecimal x5 = data.totalRevenue.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

        // --- Final Calculation ---
        BigDecimal zScore = x1.multiply(new BigDecimal("1.2"))
                .add(x2.multiply(new BigDecimal("1.4")))
                .add(x3.multiply(new BigDecimal("3.3")))
                .add(x4.multiply(new BigDecimal("0.6")))
                .add(x5.multiply(new BigDecimal("1.0")));

        ratios.setAltmanZScore(zScore);
    }

    // ==================== DATA CLASS ====================

    private static class ParsedFinancialData {

        BigDecimal marketCap;

        // Income Statement
        BigDecimal totalRevenue;
        BigDecimal grossProfit;
        BigDecimal costOfRevenue;
        BigDecimal operatingIncome;
        BigDecimal netIncome;
        BigDecimal ebit;
        BigDecimal ebitda;
        BigDecimal incomeTaxExpense;
        BigDecimal incomeBeforeTax;
        BigDecimal interestExpense;
        BigDecimal eps;
        BigDecimal epsDiluted;

        // Balance Sheet - Assets (Current)
        BigDecimal totalAssets;
        BigDecimal totalCurrentAssets;
        BigDecimal cash;
        BigDecimal shortTermInvestments;
        BigDecimal netReceivables;
        BigDecimal inventory;
        BigDecimal otherCurrentAssets;
        BigDecimal minorityInterest;

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
        BigDecimal stockBasedCompensation;

        // Computed values
        BigDecimal totalDebt;
        BigDecimal netDebt;
        BigDecimal currentLiabilities;
        BigDecimal quickAssets;
        BigDecimal tangibleEquity;
    }
}
