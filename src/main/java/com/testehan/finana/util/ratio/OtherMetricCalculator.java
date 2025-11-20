package com.testehan.finana.util.ratio;

import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates other financial metrics including:
 * - Working Capital
 * - Altman Z-Score (bankruptcy prediction)
 */
@Component
public class OtherMetricCalculator implements RatioCalculator {

    private static final BigDecimal X1_WEIGHT = new BigDecimal("1.2");
    private static final BigDecimal X2_WEIGHT = new BigDecimal("1.4");
    private static final BigDecimal X3_WEIGHT = new BigDecimal("3.3");
    private static final BigDecimal X4_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal X5_WEIGHT = new BigDecimal("1.0");

    @Override
    public void calculate(FinancialRatiosReport ratios, ParsedFinancialData data) {
        calculateWorkingCapital(ratios, data);
        calculateAltmanZScore(ratios, data);
    }

    @Override
    public String getCategoryName() {
        return "Other Metrics";
    }

    private void calculateWorkingCapital(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalCurrentAssets != null && data.currentLiabilities != null) {
            BigDecimal workingCapital = data.totalCurrentAssets.subtract(data.currentLiabilities);
            ratios.setWorkingCapital(workingCapital);
        } else {
            ratios.setWorkingCapital(null);
        }
    }

    private void calculateAltmanZScore(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalAssets == null || data.totalAssets.compareTo(BigDecimal.ZERO) <= 0) {
            ratios.setAltmanZScore(null);
            return;
        }

        if (data.totalCurrentAssets == null || data.currentLiabilities == null ||
                data.ebit == null || data.totalRevenue == null || data.totalLiabilities == null) {
            ratios.setAltmanZScore(null);
            return;
        }

        // X1: Working Capital / Total Assets
        BigDecimal workingCapital = data.totalCurrentAssets.subtract(data.currentLiabilities);
        BigDecimal x1 = workingCapital.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

        // X2: Retained Earnings / Total Assets
        BigDecimal retainedEarnings = (data.retainedEarnings != null) ? data.retainedEarnings : BigDecimal.ZERO;
        BigDecimal x2 = retainedEarnings.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

        // X3: EBIT / Total Assets
        BigDecimal x3 = data.ebit.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

        // X4: Market Value of Equity / Total Liabilities
        BigDecimal equityValue;
        if (data.marketCap != null && data.marketCap.compareTo(BigDecimal.ZERO) > 0) {
            equityValue = data.marketCap;
        } else {
            equityValue = (data.totalShareholderEquity != null) ? data.totalShareholderEquity : BigDecimal.ZERO;
        }

        BigDecimal x4 = BigDecimal.ZERO;
        if (data.totalLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            x4 = equityValue.divide(data.totalLiabilities, 4, RoundingMode.HALF_UP);
        }

        // X5: Sales / Total Assets
        BigDecimal x5 = data.totalRevenue.divide(data.totalAssets, 4, RoundingMode.HALF_UP);

        // Final Calculation
        BigDecimal zScore = x1.multiply(X1_WEIGHT)
                .add(x2.multiply(X2_WEIGHT))
                .add(x3.multiply(X3_WEIGHT))
                .add(x4.multiply(X4_WEIGHT))
                .add(x5.multiply(X5_WEIGHT));

        ratios.setAltmanZScore(zScore);
    }
}
