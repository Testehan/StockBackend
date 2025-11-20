package com.testehan.finana.util.ratio;

import com.testehan.finana.model.finstatement.BalanceSheetReport;
import com.testehan.finana.model.finstatement.CashFlowReport;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProfitabilityRatioCalculator}.
 */
class ProfitabilityRatioCalculatorTest {

    private ProfitabilityRatioCalculator calculator;
    private FinancialRatiosReport ratios;

    @BeforeEach
    void setUp() {
        calculator = new ProfitabilityRatioCalculator();
        ratios = new FinancialRatiosReport();
    }

    private ParsedFinancialData createData(BigDecimal totalRevenue,
                                            BigDecimal grossProfit,
                                            BigDecimal netIncome,
                                            BigDecimal operatingIncome,
                                            BigDecimal ebit,
                                            BigDecimal ebitda,
                                            BigDecimal stockBasedCompensation,
                                            BigDecimal totalAssets,
                                            BigDecimal totalShareholderEquity,
                                            BigDecimal shortTermDebt,
                                            BigDecimal longTermDebt,
                                            BigDecimal capitalLeaseObligations,
                                            BigDecimal minorityInterest,
                                            BigDecimal cash,
                                            BigDecimal shortTermInvestments,
                                            BigDecimal incomeBeforeTax,
                                            BigDecimal incomeTaxExpense) {
        CompanyOverview overview = new CompanyOverview();
        IncomeReport income = new IncomeReport();
        BalanceSheetReport balance = new BalanceSheetReport();
        CashFlowReport cashFlow = new CashFlowReport();

        // Set income statement values
        income.setRevenue(totalRevenue != null ? totalRevenue.toPlainString() : null);
        income.setGrossProfit(grossProfit != null ? grossProfit.toPlainString() : null);
        income.setNetIncome(netIncome != null ? netIncome.toPlainString() : null);
        income.setOperatingIncome(operatingIncome != null ? operatingIncome.toPlainString() : null);
        income.setEbit(ebit != null ? ebit.toPlainString() : null);
        income.setEbitda(ebitda != null ? ebitda.toPlainString() : null);
        income.setIncomeBeforeTax(incomeBeforeTax != null ? incomeBeforeTax.toPlainString() : null);
        income.setIncomeTaxExpense(incomeTaxExpense != null ? incomeTaxExpense.toPlainString() : null);

        // Set balance sheet values
        balance.setTotalAssets(totalAssets != null ? totalAssets.toPlainString() : null);
        balance.setTotalStockholdersEquity(totalShareholderEquity != null ? totalShareholderEquity.toPlainString() : null);
        balance.setShortTermDebt(shortTermDebt != null ? shortTermDebt.toPlainString() : null);
        balance.setLongTermDebt(longTermDebt != null ? longTermDebt.toPlainString() : null);
        balance.setCapitalLeaseObligations(capitalLeaseObligations != null ? capitalLeaseObligations.toPlainString() : null);
        balance.setMinorityInterest(minorityInterest != null ? minorityInterest.toPlainString() : null);
        balance.setCashAndCashEquivalents(cash != null ? cash.toPlainString() : null);
        balance.setShortTermInvestments(shortTermInvestments != null ? shortTermInvestments.toPlainString() : null);

        // Set cash flow values
        cashFlow.setStockBasedCompensation(stockBasedCompensation != null ? stockBasedCompensation.toPlainString() : null);

        return ParsedFinancialData.parse(overview, income, balance, cashFlow);
    }

    // ==================== Gross Profit Margin Tests ====================

    @Test
    @DisplayName("Should calculate gross profit margin correctly")
    void calculateGrossProfitMargin_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("100000"),  // totalRevenue
            new BigDecimal("40000"),   // grossProfit
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 40000/100000 = 0.4
        assertThat(ratios.getGrossProfitMargin()).isEqualByComparingTo(new BigDecimal("0.4000"));
    }

    @Test
    @DisplayName("Should set gross profit margin to null when revenue is zero")
    void calculateGrossProfitMargin_withZeroRevenue() {
        // Given
        ParsedFinancialData data = createData(
            BigDecimal.ZERO,           // totalRevenue
            new BigDecimal("40000"),
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getGrossProfitMargin()).isNull();
    }

    // ==================== Net Profit Margin Tests ====================

    @Test
    @DisplayName("Should calculate net profit margin correctly")
    void calculateNetProfitMargin_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("100000"),  // totalRevenue
            null,
            new BigDecimal("10000"),   // netIncome
            null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 10000/100000 = 0.1
        assertThat(ratios.getNetProfitMargin()).isEqualByComparingTo(new BigDecimal("0.1000"));
    }

    // ==================== Operating Profit Margin Tests ====================

    @Test
    @DisplayName("Should calculate operating profit margin using operating income")
    void calculateOperatingProfitMargin_withOperatingIncome() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("100000"),  // totalRevenue
            null, null,
            new BigDecimal("15000"),   // operatingIncome
            new BigDecimal("12000"),   // ebit (should use operatingIncome, not EBIT)
            null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 15000/100000 = 0.15 (uses operatingIncome)
        assertThat(ratios.getOperatingProfitMargin()).isEqualByComparingTo(new BigDecimal("0.1500"));
    }

    @Test
    @DisplayName("Should calculate operating profit margin with SafeParser behavior (null becomes ZERO)")
    void calculateOperatingProfitMargin_withSafeParserNulls() {
        // Given
        // SafeParser converts null operatingIncome and EBIT to ZERO
        // Since operatingIncome is ZERO (not null), the ternary returns ZERO
        ParsedFinancialData data = createData(
            new BigDecimal("100000"),  // totalRevenue
            null, null,
            null,                      // operatingIncome null -> ZERO
            null,                      // ebit null -> ZERO
            null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - both are ZERO, and since ZERO is not null, it uses ZERO/revenue = 0
        assertThat(ratios.getOperatingProfitMargin()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    // ==================== EBITDA Margin Tests ====================

    @Test
    @DisplayName("Should calculate EBITDA margin correctly")
    void calculateEbitdaMargin_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("100000"),  // totalRevenue
            null, null, null, null,
            new BigDecimal("20000"),   // ebitda
            null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 20000/100000 = 0.2
        assertThat(ratios.getEbitdaMargin()).isEqualByComparingTo(new BigDecimal("0.2000"));
    }

    // ==================== Adjusted EBITDA Margin Tests ====================

    @Test
    @DisplayName("Should calculate adjusted EBITDA margin with stock-based compensation")
    void calculateAdjustedEbitdaMargin_withSBC() {
        // Given
        // adjustedEbitda = ebitda + stockBasedCompensation = 20000 + 3000 = 23000
        ParsedFinancialData data = createData(
            new BigDecimal("100000"),  // totalRevenue
            null, null, null, null,
            new BigDecimal("20000"),   // ebitda
            new BigDecimal("3000"),    // stockBasedCompensation
            null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 23000/100000 = 0.23
        assertThat(ratios.getAdjustedEbitdaMargin()).isEqualByComparingTo(new BigDecimal("0.2300"));
    }

    @Test
    @DisplayName("Should calculate adjusted EBITDA margin without stock-based compensation")
    void calculateAdjustedEbitdaMargin_withoutSBC() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("100000"),
            null, null, null, null,
            new BigDecimal("20000"),
            null,                      // stockBasedCompensation null
            null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 20000/100000 = 0.2
        assertThat(ratios.getAdjustedEbitdaMargin()).isEqualByComparingTo(new BigDecimal("0.2000"));
    }

    // ==================== Return on Assets Tests ====================

    @Test
    @DisplayName("Should calculate return on assets correctly")
    void calculateReturnOnAssets_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            null, null,
            new BigDecimal("10000"),   // netIncome
            null, null, null, null,
            new BigDecimal("100000"),  // totalAssets
            null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 10000/100000 = 0.1
        assertThat(ratios.getReturnOnAssets()).isEqualByComparingTo(new BigDecimal("0.1000"));
    }

    // ==================== Return on Equity Tests ====================

    @Test
    @DisplayName("Should calculate return on equity correctly")
    void calculateReturnOnEquity_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            null, null,
            new BigDecimal("10000"),   // netIncome
            null, null, null, null, null,
            new BigDecimal("50000"),   // totalShareholderEquity
            null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 10000/50000 = 0.2
        assertThat(ratios.getReturnOnEquity()).isEqualByComparingTo(new BigDecimal("0.2000"));
    }

    // ==================== ROIC Tests ====================

    @Test
    @DisplayName("Should calculate ROIC correctly with all data")
    void calculateRoic_withValidData() {
        // Given
        // NOPAT = operatingIncome * (1 - taxRate)
        // taxRate = taxExpense/incomeBeforeTax = 4200/20000 = 0.21
        // NOPAT = 15000 * (1 - 0.21) = 11850
        // Invested Capital = equity + debt + leases + minority - cash - investments
        // = 50000 + 30000 + 5000 + 2000 - 10000 - 5000 = 72000
        // ROIC = 11850/72000 = 0.1646
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("15000"),   // operatingIncome
            null, null, null, null,
            new BigDecimal("50000"),   // totalShareholderEquity
            new BigDecimal("20000"),   // shortTermDebt
            new BigDecimal("10000"),   // longTermDebt
            new BigDecimal("5000"),    // capitalLeaseObligations
            new BigDecimal("2000"),    // minorityInterest
            new BigDecimal("10000"),   // cash
            new BigDecimal("5000"),    // shortTermInvestments
            new BigDecimal("20000"),   // incomeBeforeTax
            new BigDecimal("4200")     // incomeTaxExpense
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getRoic()).isEqualByComparingTo(new BigDecimal("0.1646"));
    }

    @Test
    @DisplayName("Should calculate ROIC with default tax rate when tax data unavailable")
    void calculateRoic_withDefaultTaxRate() {
        // Given
        // No tax data, uses default tax rate of 0.21
        // NOPAT = 15000 * (1 - 0.21) = 11850
        // Invested Capital = 50000 + 20000 = 70000
        // ROIC = 11850/70000 = 0.1693
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("15000"),   // operatingIncome
            null, null, null, null,
            new BigDecimal("50000"),   // totalShareholderEquity
            new BigDecimal("20000"),   // shortTermDebt
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getRoic()).isEqualByComparingTo(new BigDecimal("0.1693"));
    }

    @Test
    @DisplayName("Should cap ROIC at 9.9999 when invested capital is zero but NOPAT is positive")
    void calculateRoic_withZeroInvestedCapital() {
        // Given
        // Invested Capital = 0 (no equity, no debt)
        // NOPAT = 15000 * 0.79 = 11850 (positive)
        // ROIC capped at 9.9999
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("15000"),
            null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getRoic()).isEqualByComparingTo(new BigDecimal("9.9999"));
    }

    @Test
    @DisplayName("Should return zero ROIC when invested capital and NOPAT are both zero")
    void calculateRoic_withZeroInvestedCapitalAndZeroNopat() {
        // Given
        // Invested Capital = 0, NOPAT = 0
        ParsedFinancialData data = createData(
            null, null, null,
            null,                      // operatingIncome null (NOPAT will be 0)
            null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getRoic()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate ROIC with actual operating income")
    void calculateRoic_withOperatingIncome() {
        // Given
        // NOPAT = operatingIncome * (1 - taxRate) = 12000 * 0.79 = 9480
        // Invested Capital = 50000 + 20000 = 70000
        // ROIC = 9480/70000 = 0.1354
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("12000"),   // operatingIncome
            null, null, null, null,
            new BigDecimal("50000"),
            new BigDecimal("20000"),
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getRoic()).isEqualByComparingTo(new BigDecimal("0.1354"));
    }

    // ==================== Tax Rate Edge Cases ====================

    @Test
    @DisplayName("Should use default tax rate when calculated rate is negative")
    void calculateRoic_withNegativeTaxRate() {
        // Given
        // Tax expense is negative (tax benefit), calculated rate = -2100/20000 = -0.105
        // Should use default 0.21 instead
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("15000"),
            null, null, null, null,
            new BigDecimal("50000"),
            new BigDecimal("20000"),
            null, null, null, null, null,
            new BigDecimal("20000"),
            new BigDecimal("-2100")    // negative tax expense
        );

        // When
        calculator.calculate(ratios, data);

        // Then - uses default tax rate 0.21
        assertThat(ratios.getRoic()).isEqualByComparingTo(new BigDecimal("0.1693"));
    }

    @Test
    @DisplayName("Should use default tax rate when calculated rate exceeds cap")
    void calculateRoic_withHighTaxRate() {
        // Given
        // Tax rate = 8000/20000 = 0.4 (exceeds 0.35 cap)
        // Should use default 0.21
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("15000"),
            null, null, null, null,
            new BigDecimal("50000"),
            new BigDecimal("20000"),
            null, null, null, null, null,
            new BigDecimal("20000"),
            new BigDecimal("8000")     // high tax expense (40% rate)
        );

        // When
        calculator.calculate(ratios, data);

        // Then - uses default tax rate 0.21
        assertThat(ratios.getRoic()).isEqualByComparingTo(new BigDecimal("0.1693"));
    }

    // ==================== Category Name Test ====================

    @Test
    @DisplayName("Should return correct category name")
    void getCategoryName() {
        assertThat(calculator.getCategoryName()).isEqualTo("Profitability Ratios");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle all null values gracefully")
    void calculate_withAllNullValues() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getGrossProfitMargin()).isNull();
        assertThat(ratios.getNetProfitMargin()).isNull();
        assertThat(ratios.getOperatingProfitMargin()).isNull();
        assertThat(ratios.getEbitdaMargin()).isNull();
        assertThat(ratios.getAdjustedEbitdaMargin()).isNull();
        assertThat(ratios.getReturnOnAssets()).isNull();
        assertThat(ratios.getReturnOnEquity()).isNull();
        assertThat(ratios.getRoic()).isEqualByComparingTo(BigDecimal.ZERO); // ROIC handles nulls
    }

    @Test
    @DisplayName("Should handle very large numbers correctly")
    void calculate_withLargeNumbers() {
        // Given - billions
        ParsedFinancialData data = createData(
            new BigDecimal("500000000000"),  // totalRevenue: $500B
            new BigDecimal("200000000000"),  // grossProfit: $200B
            new BigDecimal("50000000000"),   // netIncome: $50B
            new BigDecimal("75000000000"),   // operatingIncome: $75B
            new BigDecimal("60000000000"),   // ebit: $60B
            new BigDecimal("100000000000"),  // ebitda: $100B
            new BigDecimal("5000000000"),    // stockBasedCompensation: $5B
            new BigDecimal("1000000000000"), // totalAssets: $1T
            new BigDecimal("500000000000"),  // totalShareholderEquity: $500B
            null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // Gross Margin: 200B/500B = 0.4
        assertThat(ratios.getGrossProfitMargin()).isEqualByComparingTo(new BigDecimal("0.4000"));
        // Net Margin: 50B/500B = 0.1
        assertThat(ratios.getNetProfitMargin()).isEqualByComparingTo(new BigDecimal("0.1000"));
        // Operating Margin: 75B/500B = 0.15
        assertThat(ratios.getOperatingProfitMargin()).isEqualByComparingTo(new BigDecimal("0.1500"));
        // EBITDA Margin: 100B/500B = 0.2
        assertThat(ratios.getEbitdaMargin()).isEqualByComparingTo(new BigDecimal("0.2000"));
        // Adjusted EBITDA Margin: 105B/500B = 0.21
        assertThat(ratios.getAdjustedEbitdaMargin()).isEqualByComparingTo(new BigDecimal("0.2100"));
        // ROA: 50B/1T = 0.05
        assertThat(ratios.getReturnOnAssets()).isEqualByComparingTo(new BigDecimal("0.0500"));
        // ROE: 50B/500B = 0.1
        assertThat(ratios.getReturnOnEquity()).isEqualByComparingTo(new BigDecimal("0.1000"));
    }

    @Test
    @DisplayName("Should calculate all ratios together correctly")
    void calculate_allRatiosTogether() {
        // Given - realistic company data
        ParsedFinancialData data = createData(
            new BigDecimal("100000"),  // totalRevenue
            new BigDecimal("40000"),   // grossProfit
            new BigDecimal("10000"),   // netIncome
            new BigDecimal("15000"),   // operatingIncome
            new BigDecimal("12000"),   // ebit
            new BigDecimal("20000"),   // ebitda
            new BigDecimal("2000"),    // stockBasedCompensation
            new BigDecimal("200000"),  // totalAssets
            new BigDecimal("80000"),   // totalShareholderEquity
            new BigDecimal("30000"),   // shortTermDebt
            new BigDecimal("20000"),   // longTermDebt
            new BigDecimal("5000"),    // capitalLeaseObligations
            new BigDecimal("1000"),    // minorityInterest
            new BigDecimal("15000"),   // cash
            new BigDecimal("5000"),    // shortTermInvestments
            new BigDecimal("25000"),   // incomeBeforeTax
            new BigDecimal("5250")     // incomeTaxExpense (21%)
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // Gross Margin: 40000/100000 = 0.4
        assertThat(ratios.getGrossProfitMargin()).isEqualByComparingTo(new BigDecimal("0.4000"));
        // Net Margin: 10000/100000 = 0.1
        assertThat(ratios.getNetProfitMargin()).isEqualByComparingTo(new BigDecimal("0.1000"));
        // Operating Margin: 15000/100000 = 0.15
        assertThat(ratios.getOperatingProfitMargin()).isEqualByComparingTo(new BigDecimal("0.1500"));
        // EBITDA Margin: 20000/100000 = 0.2
        assertThat(ratios.getEbitdaMargin()).isEqualByComparingTo(new BigDecimal("0.2000"));
        // Adjusted EBITDA Margin: 22000/100000 = 0.22
        assertThat(ratios.getAdjustedEbitdaMargin()).isEqualByComparingTo(new BigDecimal("0.2200"));
        // ROA: 10000/200000 = 0.05
        assertThat(ratios.getReturnOnAssets()).isEqualByComparingTo(new BigDecimal("0.0500"));
        // ROE: 10000/80000 = 0.125
        assertThat(ratios.getReturnOnEquity()).isEqualByComparingTo(new BigDecimal("0.1250"));
        // ROIC: NOPAT = 15000 * 0.79 = 11850, Invested Capital = 116000
        // ROIC = 11850 / 116000 = 0.1022
        assertThat(ratios.getRoic()).isEqualByComparingTo(new BigDecimal("0.1022"));
    }
}
