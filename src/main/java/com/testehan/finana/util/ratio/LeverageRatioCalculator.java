package com.testehan.finana.util.ratio;

import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates leverage ratios including:
 * - Debt to Assets Ratio
 * - Debt to Equity Ratio
 * - Interest Coverage Ratio
 * - Net Debt to EBITDA
 * - Debt Service Coverage Ratio
 */
@Component
public class LeverageRatioCalculator implements RatioCalculator {

    @Override
    public void calculate(FinancialRatiosReport ratios, ParsedFinancialData data) {
        calculateDebtToAssetsRatio(ratios, data);
        calculateDebtToEquityRatio(ratios, data);
        calculateInterestCoverageRatio(ratios, data);
        calculateNetDebtToEbitda(ratios, data);
        calculateDebtServiceCoverageRatio(ratios, data);
    }

    @Override
    public String getCategoryName() {
        return "Leverage Ratios";
    }

    private void calculateDebtToAssetsRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalAssets != null && data.totalLiabilities != null &&
                data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDebtToAssetsRatio(data.totalLiabilities.divide(data.totalAssets, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDebtToAssetsRatio(null);
        }
    }

    private void calculateDebtToEquityRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalShareholderEquity != null && data.totalDebt != null &&
                data.totalShareholderEquity.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDebtToEquityRatio(data.totalDebt.divide(data.totalShareholderEquity, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDebtToEquityRatio(null);
        }
    }

    private void calculateInterestCoverageRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.ebit != null && data.interestExpense != null) {
            BigDecimal interestAbs = data.interestExpense.abs();
            if (interestAbs.compareTo(BigDecimal.ZERO) > 0) {
                ratios.setInterestCoverageRatio(data.ebit.divide(interestAbs, 4, RoundingMode.HALF_UP));
            } else {
                ratios.setInterestCoverageRatio(null);
            }
        } else {
            ratios.setInterestCoverageRatio(null);
        }
    }

    private void calculateNetDebtToEbitda(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.netDebt != null && data.ebitda != null &&
                data.ebitda.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setNetDebtToEbitda(data.netDebt.divide(data.ebitda, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setNetDebtToEbitda(null);
        }
    }

    private void calculateDebtServiceCoverageRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.ebitda != null && data.interestExpense != null) {
            BigDecimal interest = data.interestExpense.abs();
            BigDecimal principal = (data.shortTermDebt != null) ? data.shortTermDebt : BigDecimal.ZERO;
            BigDecimal totalDebtService = interest.add(principal);

            if (totalDebtService.compareTo(BigDecimal.ZERO) > 0) {
                ratios.setDebtServiceCoverageRatio(data.ebitda.divide(totalDebtService, 4, RoundingMode.HALF_UP));
            } else {
                ratios.setDebtServiceCoverageRatio(null);
            }
        } else {
            ratios.setDebtServiceCoverageRatio(null);
        }
    }
}
