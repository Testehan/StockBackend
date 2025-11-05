package com.testehan.finana.util.ratio;

import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates per share metrics including:
 * - Earnings Per Share (Basic)
 * - Earnings Per Share (Diluted)
 * - Book Value Per Share
 * - Tangible Book Value Per Share
 * - Sales Per Share
 * - Free Cash Flow Per Share
 * - Operating Cash Flow Per Share
 * - Cash Per Share
 */
@Component
public class PerShareMetricCalculator implements RatioCalculator {

    @Override
    public void calculate(FinancialRatiosReport ratios, ParsedFinancialData data) {
        calculateEarningsPerShareBasic(ratios, data);
        calculateEarningsPerShareDiluted(ratios, data);
        calculateBookValuePerShare(ratios, data);
        calculateTangibleBookValuePerShare(ratios, data);
        calculateSalesPerShare(ratios, data);
        calculateFreeCashFlowPerShare(ratios, data);
        calculateOperatingCashFlowPerShare(ratios, data);
        calculateCashPerShare(ratios, data);
    }

    @Override
    public String getCategoryName() {
        return "Per Share Metrics";
    }

    private void calculateEarningsPerShareBasic(FinancialRatiosReport ratios, ParsedFinancialData data) {
        ratios.setEarningsPerShareBasic(data.eps);
    }

    private void calculateEarningsPerShareDiluted(FinancialRatiosReport ratios, ParsedFinancialData data) {
        ratios.setEarningsPerShareDiluted(data.epsDiluted);
    }

    private void calculateBookValuePerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalShareholderEquity == null) {
            ratios.setBookValuePerShare(null);
            return;
        }

        BigDecimal shares = getBasicShares(data);
        if (shares != null) {
            ratios.setBookValuePerShare(data.totalShareholderEquity.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setBookValuePerShare(null);
        }
    }

    private void calculateTangibleBookValuePerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.tangibleEquity == null) {
            ratios.setTangibleBookValuePerShare(null);
            return;
        }

        BigDecimal shares = getBasicShares(data);
        if (shares != null) {
            ratios.setTangibleBookValuePerShare(data.tangibleEquity.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setTangibleBookValuePerShare(null);
        }
    }

    private void calculateSalesPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue == null) {
            ratios.setSalesPerShare(null);
            return;
        }

        BigDecimal shares = getBasicShares(data);
        if (shares != null) {
            ratios.setSalesPerShare(data.totalRevenue.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setSalesPerShare(null);
        }
    }

    private void calculateFreeCashFlowPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.operatingCashflow == null || data.capitalExpenditures == null) {
            ratios.setFreeCashFlowPerShare(null);
            return;
        }

        BigDecimal fcf = data.operatingCashflow.subtract(data.capitalExpenditures.abs());
        BigDecimal shares = getBasicShares(data);

        if (shares != null) {
            ratios.setFreeCashFlowPerShare(fcf.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setFreeCashFlowPerShare(null);
        }
    }

    private void calculateOperatingCashFlowPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.operatingCashflow == null) {
            ratios.setOperatingCashFlowPerShare(null);
            return;
        }

        BigDecimal shares = getBasicShares(data);
        if (shares != null) {
            ratios.setOperatingCashFlowPerShare(data.operatingCashflow.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setOperatingCashFlowPerShare(null);
        }
    }

    private void calculateCashPerShare(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.cash == null) {
            ratios.setCashPerShare(null);
            return;
        }

        BigDecimal shares = getBasicShares(data);
        if (shares != null) {
            ratios.setCashPerShare(data.cash.divide(shares, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setCashPerShare(null);
        }
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
