package com.testehan.finana.util.ratio;

import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates dividend metrics including:
 * - Dividend Per Share
 * - Dividend Yield (placeholder - requires stock price)
 * - Dividend Payout Ratio
 * - Buyback Yield (placeholder - requires buyback data)
 */
@Component
public class DividendMetricCalculator implements RatioCalculator {

    @Override
    public void calculate(FinancialRatiosReport ratios, ParsedFinancialData data) {
        calculateDividendPerShare(ratios, data);
        calculateDividendYield(ratios, data);
        calculateDividendPayoutRatio(ratios, data);
        calculateBuybackYield(ratios, data);
    }

    @Override
    public String getCategoryName() {
        return "Dividend Metrics";
    }

    private void calculateDividendPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal totalDividendsPaid = BigDecimal.ZERO;

        if (data.dividendPayoutCommonStock != null &&
                data.dividendPayoutCommonStock.compareTo(BigDecimal.ZERO) != 0) {
            totalDividendsPaid = data.dividendPayoutCommonStock.abs();
        } else if (data.dividendPayout != null) {
            totalDividendsPaid = data.dividendPayout.abs();
        }

        BigDecimal shares = getBasicShares(data);

        if (shares != null && totalDividendsPaid.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDividendPerShare(totalDividendsPaid.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDividendPerShare(null);
        }
    }

    private void calculateDividendYield(FinancialRatiosReport ratios, ParsedFinancialData data) {
        ratios.setDividendYield(null);
    }

    private void calculateDividendPayoutRatio(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal totalDividends = BigDecimal.ZERO;

        if (data.dividendPayoutCommonStock != null &&
                data.dividendPayoutCommonStock.compareTo(BigDecimal.ZERO) != 0) {
            totalDividends = data.dividendPayoutCommonStock.abs();
        } else if (data.dividendPayout != null) {
            totalDividends = data.dividendPayout.abs();
        }

        if (data.netIncome != null && data.netIncome.compareTo(BigDecimal.ZERO) > 0 &&
                totalDividends.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDividendPayoutRatio(totalDividends.divide(data.netIncome, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setDividendPayoutRatio(null);
        }
    }

    private void calculateBuybackYield(FinancialRatiosReport ratios, ParsedFinancialData data) {
        ratios.setBuybackYield(null);
    }

    private BigDecimal getBasicShares(ParsedFinancialData data) {
        if (data.sharesOutstandingBasic != null && data.sharesOutstandingBasic.compareTo(BigDecimal.ZERO) > 0) {
            return data.sharesOutstandingBasic;
        } else if (data.sharesOutstanding != null && data.sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            return data.sharesOutstanding;
        }
        return null;
    }
}
