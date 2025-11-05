package com.testehan.finana.util.ratio;

import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates efficiency ratios including:
 * - Asset Turnover
 * - Inventory Turnover
 * - Receivables Turnover
 * - Payables Turnover
 * - Days Sales Outstanding (DSO)
 * - Days Inventory Outstanding (DIO)
 * - Days Payables Outstanding (DPO)
 * - Cash Conversion Cycle (CCC)
 * - Sales to Capital Ratio
 */
@Component
public class EfficiencyRatioCalculator implements RatioCalculator {

    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");

    @Override
    public void calculate(FinancialRatiosReport ratios, ParsedFinancialData data) {
        calculateAssetTurnover(ratios, data);
        calculateInventoryTurnover(ratios, data);
        calculateReceivablesTurnover(ratios, data);
        calculatePayablesTurnover(ratios, data);
        calculateDaysSalesOutstanding(ratios, data);
        calculateDaysInventoryOutstanding(ratios, data);
        calculateDaysPayablesOutstanding(ratios, data);
        calculateCashConversionCycle(ratios, data);
        calculateSalesToCapitalRatio(ratios, data);
    }

    @Override
    public String getCategoryName() {
        return "Efficiency Ratios";
    }

    private void calculateAssetTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue != null && data.totalAssets != null &&
                data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setAssetTurnover(data.totalRevenue.divide(data.totalAssets, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setAssetTurnover(null);
        }
    }

    private void calculateInventoryTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.costOfRevenue != null && data.inventory != null &&
                data.inventory.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setInventoryTurnover(data.costOfRevenue.divide(data.inventory, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setInventoryTurnover(null);
        }
    }

    private void calculateReceivablesTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue != null && data.netReceivables != null &&
                data.netReceivables.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setReceivablesTurnover(data.totalRevenue.divide(data.netReceivables, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setReceivablesTurnover(null);
        }
    }

    private void calculatePayablesTurnover(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.costOfRevenue != null && data.currentAccountsPayable != null &&
                data.currentAccountsPayable.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setPayablesTurnover(data.costOfRevenue.divide(data.currentAccountsPayable, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setPayablesTurnover(null);
        }
    }

    private void calculateDaysSalesOutstanding(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue != null && data.netReceivables != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dso = data.netReceivables.multiply(DAYS_IN_YEAR)
                    .divide(data.totalRevenue, 4, RoundingMode.HALF_UP);
            ratios.setDaysSalesOutstanding(dso);
        } else {
            ratios.setDaysSalesOutstanding(null);
        }
    }

    private void calculateDaysInventoryOutstanding(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.costOfRevenue != null && data.inventory != null &&
                data.costOfRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal numerator = data.inventory.multiply(DAYS_IN_YEAR);
            ratios.setDaysInventoryOutstanding(numerator.divide(data.costOfRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDaysInventoryOutstanding(null);
        }
    }

    private void calculateDaysPayablesOutstanding(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.costOfRevenue != null && data.currentAccountsPayable != null &&
                data.costOfRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal numerator = data.currentAccountsPayable.multiply(DAYS_IN_YEAR);
            ratios.setDaysPayablesOutstanding(numerator.divide(data.costOfRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDaysPayablesOutstanding(null);
        }
    }

    private void calculateCashConversionCycle(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal dso = ratios.getDaysSalesOutstanding();
        BigDecimal dpo = ratios.getDaysPayablesOutstanding();
        BigDecimal dio = ratios.getDaysInventoryOutstanding();

        if (dso != null && dpo != null) {
            BigDecimal safeDio = (dio != null) ? dio : BigDecimal.ZERO;
            BigDecimal ccc = safeDio.add(dso).subtract(dpo);
            ratios.setCashConversionCycle(ccc);
        } else {
            ratios.setCashConversionCycle(null);
        }
    }

    private void calculateSalesToCapitalRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue != null && data.totalDebt != null &&
                data.totalShareholderEquity != null && data.cash != null) {
            BigDecimal investedCapital = data.totalDebt
                    .add(data.totalShareholderEquity)
                    .subtract(data.cash);
            if (data.shortTermInvestments != null) {
                investedCapital = investedCapital.subtract(data.shortTermInvestments);
            }

            if (investedCapital.compareTo(BigDecimal.ZERO) > 0) {
                ratios.setSalesToCapitalRatio(data.totalRevenue.divide(investedCapital, 4, RoundingMode.HALF_UP));
            } else {
                ratios.setSalesToCapitalRatio(null);
            }
        } else {
            ratios.setSalesToCapitalRatio(null);
        }
    }
}
