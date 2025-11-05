package com.testehan.finana.util.ratio;

import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates profitability ratios including:
 * - Gross Profit Margin
 * - Net Profit Margin
 * - Operating Profit Margin
 * - EBITDA Margin
 * - Adjusted EBITDA Margin
 * - Return on Assets (ROA)
 * - Return on Equity (ROE)
 * - Return on Invested Capital (ROIC)
 */
@Component
public class ProfitabilityRatioCalculator implements RatioCalculator {

    private static final BigDecimal TAX_RATE_CAP = new BigDecimal("0.35");
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.21");
    private static final BigDecimal INFINITE_ROIC_CAP = new BigDecimal("9.9999");

    @Override
    public void calculate(FinancialRatiosReport ratios, ParsedFinancialData data) {
        calculateGrossProfitMargin(ratios, data);
        calculateNetProfitMargin(ratios, data);
        calculateOperatingProfitMargin(ratios, data);
        calculateEbitdaMargin(ratios, data);
        calculateAdjustedEbitdaMargin(ratios, data);
        calculateReturnOnAssets(ratios, data);
        calculateReturnOnEquity(ratios, data);
        calculateRoic(ratios, data);
    }

    @Override
    public String getCategoryName() {
        return "Profitability Ratios";
    }

    private void calculateGrossProfitMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue != null && data.grossProfit != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setGrossProfitMargin(data.grossProfit.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setGrossProfitMargin(null);
        }
    }

    private void calculateNetProfitMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue != null && data.netIncome != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setNetProfitMargin(data.netIncome.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setNetProfitMargin(null);
        }
    }

    private void calculateOperatingProfitMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue != null && data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal numerator = (data.operatingIncome != null) ? data.operatingIncome : data.ebit;
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
        if (data.totalRevenue != null && data.ebitda != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setEbitdaMargin(data.ebitda.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setEbitdaMargin(null);
        }
    }

    private void calculateAdjustedEbitdaMargin(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalRevenue != null && data.ebitda != null &&
                data.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal adjustedEbitda = data.ebitda;
            if (data.stockBasedCompensation != null) {
                adjustedEbitda = adjustedEbitda.add(data.stockBasedCompensation);
            }
            ratios.setAdjustedEbitdaMargin(adjustedEbitda.divide(data.totalRevenue, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setAdjustedEbitdaMargin(null);
        }
    }

    private void calculateReturnOnAssets(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalAssets != null && data.netIncome != null &&
                data.totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setReturnOnAssets(data.netIncome.divide(data.totalAssets, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setReturnOnAssets(null);
        }
    }

    private void calculateReturnOnEquity(FinancialRatiosReport ratios, ParsedFinancialData data) {
        if (data.totalShareholderEquity != null && data.netIncome != null &&
                data.totalShareholderEquity.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setReturnOnEquity(data.netIncome.divide(data.totalShareholderEquity, 4, RoundingMode.HALF_UP));
        } else {
            ratios.setReturnOnEquity(null);
        }
    }

    public void calculateRoic(FinancialRatiosReport ratios, ParsedFinancialData data) {
        BigDecimal taxRate = calculateTaxRate(data);
        BigDecimal nopat = calculateNopat(data, taxRate);
        BigDecimal investedCapital = calculateInvestedCapital(data);

        if (investedCapital.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setRoic(nopat.divide(investedCapital, 4, RoundingMode.HALF_UP));
        } else if (nopat.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setRoic(INFINITE_ROIC_CAP);
        } else {
            ratios.setRoic(BigDecimal.ZERO);
        }
    }

    private BigDecimal calculateTaxRate(ParsedFinancialData data) {
        BigDecimal incomeBeforeTax = data.incomeBeforeTax;
        BigDecimal taxExpense = data.incomeTaxExpense;

        if (incomeBeforeTax != null && incomeBeforeTax.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taxRate = taxExpense.divide(incomeBeforeTax, 4, RoundingMode.HALF_UP);
            if (taxRate.compareTo(BigDecimal.ZERO) < 0) taxRate = DEFAULT_TAX_RATE;
            if (taxRate.compareTo(TAX_RATE_CAP) > 0) taxRate = DEFAULT_TAX_RATE;
            return taxRate;
        } else {
            return DEFAULT_TAX_RATE;
        }
    }

    private BigDecimal calculateNopat(ParsedFinancialData data, BigDecimal taxRate) {
        BigDecimal operatingIncome = (data.operatingIncome != null) ? data.operatingIncome : data.ebit;
        return operatingIncome.multiply(BigDecimal.ONE.subtract(taxRate));
    }

    private BigDecimal calculateInvestedCapital(ParsedFinancialData data) {
        BigDecimal investedCapital = BigDecimal.ZERO;

        if (data.totalShareholderEquity != null) {
            investedCapital = investedCapital.add(data.totalShareholderEquity);
        }
        if (data.totalDebt != null) {
            investedCapital = investedCapital.add(data.totalDebt);
        }
        if (data.capitalLeaseObligations != null) {
            investedCapital = investedCapital.add(data.capitalLeaseObligations);
        }
        if (data.minorityInterest != null) {
            investedCapital = investedCapital.add(data.minorityInterest);
        }
        if (data.cash != null) {
            investedCapital = investedCapital.subtract(data.cash);
        }
        if (data.shortTermInvestments != null) {
            investedCapital = investedCapital.subtract(data.shortTermInvestments);
        }

        return investedCapital;
    }
}
