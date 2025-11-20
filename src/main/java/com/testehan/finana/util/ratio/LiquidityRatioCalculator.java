package com.testehan.finana.util.ratio;

import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates liquidity ratios including:
 * - Current Ratio
 * - Quick Ratio
 * - Cash Ratio
 */
@Component
public class LiquidityRatioCalculator implements RatioCalculator {

    @Override
    public void calculate(FinancialRatiosReport ratios, ParsedFinancialData data) {
        calculateCurrentRatio(ratios, data);
        calculateQuickRatio(ratios, data);
        calculateCashRatio(ratios, data);
    }

    @Override
    public String getCategoryName() {
        return "Liquidity Ratios";
    }

    private void calculateCurrentRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalCurrentAssets != null && data.currentLiabilities != null &&
                data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setCurrentRatio(data.totalCurrentAssets.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setCurrentRatio(null);
        }
    }

    private void calculateQuickRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.currentLiabilities != null && data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal numerator = null;

            if (data.quickAssets != null) {
                numerator = data.quickAssets;
            } else if (data.totalCurrentAssets != null) {
                numerator = data.totalCurrentAssets;
                if (data.inventory != null) {
                    numerator = numerator.subtract(data.inventory);
                }
            }

            if (numerator != null) {
                ratios.setQuickRatio(numerator.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
            } else {
                ratios.setQuickRatio(null);
            }
        } else {
            ratios.setQuickRatio(null);
        }
    }

    private void calculateCashRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.currentLiabilities != null && data.currentLiabilities.compareTo(BigDecimal.ZERO) > 0) {
            if (data.cash != null) {
                ratios.setCashRatio(data.cash.divide(data.currentLiabilities, 4, RoundingMode.HALF_UP));
            } else {
                ratios.setCashRatio(null);
            }
        } else {
            ratios.setCashRatio(null);
        }
    }
}
