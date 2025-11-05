package com.testehan.finana.util.ratio;

import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates cash flow metrics including:
 * - Free Cash Flow
 * - Free Cash Flow Margin
 * - Operating Cash Flow Ratio
 * - Cash Flow to Debt Ratio
 */
@Component
public class CashFlowMetricCalculator implements RatioCalculator {

    @Override
    public void calculate(FinancialRatiosReport ratios, ParsedFinancialData data) {
        calculateFreeCashFlow(ratios, data);
        calculateFcfMargin(ratios, data);
        calculateOperatingCashFlowRatio(ratios, data);
        calculateCashFlowToDebtRatio(ratios, data);
    }

    @Override
    public String getCategoryName() {
        return "Cash Flow Metrics";
    }

    private void calculateFreeCashFlow(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.operatingCashflow != null && data.capitalExpenditures != null) {
            BigDecimal capex = data.capitalExpenditures.abs();
            BigDecimal fcf = data.operatingCashflow.subtract(capex);
            ratios.setFreeCashFlow(fcf);
        } else {
            ratios.setFreeCashFlow(null);
        }
    }

    private void calculateFcfMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (ratios.getFreeCashFlow() != null && data.totalRevenue != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setFreeCashflowMargin(ratios.getFreeCashFlow().divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setFreeCashflowMargin(null);
        }
    }

    private void calculateOperatingCashFlowRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.operatingCashflow != null && data.currentLiabilities != null &&
                data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setOperatingCashFlowRatio(data.operatingCashflow.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setOperatingCashFlowRatio(null);
        }
    }

    private void calculateCashFlowToDebtRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.operatingCashflow != null && data.totalDebt != null &&
                data.totalDebt.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setCashFlowToDebtRatio(data.operatingCashflow.divide(data.totalDebt, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setCashFlowToDebtRatio(null);
        }
    }
}
