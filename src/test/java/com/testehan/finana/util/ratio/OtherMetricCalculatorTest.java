package com.testehan.finana.util.ratio;

import com.testehan.finana.model.BalanceSheetReport;
import com.testehan.finana.model.CashFlowReport;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OtherMetricCalculator}.
 */
class OtherMetricCalculatorTest {

    private OtherMetricCalculator calculator;
    private FinancialRatiosReport ratios;

    @BeforeEach
    void setUp() {
        calculator = new OtherMetricCalculator();
        ratios = new FinancialRatiosReport();
    }

    private ParsedFinancialData createData(BigDecimal totalCurrentAssets,
                                            BigDecimal shortTermDebt,
                                            BigDecimal accountsPayable,
                                            BigDecimal otherCurrentLiabilities,
                                            BigDecimal deferredRevenue,
                                            BigDecimal totalAssets,
                                            BigDecimal totalLiabilities,
                                            BigDecimal totalShareholderEquity,
                                            BigDecimal retainedEarnings,
                                            BigDecimal ebit,
                                            BigDecimal totalRevenue,
                                            BigDecimal marketCap) {
        CompanyOverview overview = new CompanyOverview();
        IncomeReport income = new IncomeReport();
        BalanceSheetReport balance = new BalanceSheetReport();
        CashFlowReport cashFlow = new CashFlowReport();

        // Set balance sheet values
        balance.setTotalCurrentAssets(totalCurrentAssets != null ? totalCurrentAssets.toPlainString() : null);
        balance.setShortTermDebt(shortTermDebt != null ? shortTermDebt.toPlainString() : null);
        balance.setAccountPayables(accountsPayable != null ? accountsPayable.toPlainString() : null);
        balance.setOtherCurrentLiabilities(otherCurrentLiabilities != null ? otherCurrentLiabilities.toPlainString() : null);
        balance.setDeferredRevenue(deferredRevenue != null ? deferredRevenue.toPlainString() : null);
        balance.setTotalAssets(totalAssets != null ? totalAssets.toPlainString() : null);
        balance.setTotalLiabilities(totalLiabilities != null ? totalLiabilities.toPlainString() : null);
        balance.setTotalStockholdersEquity(totalShareholderEquity != null ? totalShareholderEquity.toPlainString() : null);
        balance.setRetainedEarnings(retainedEarnings != null ? retainedEarnings.toPlainString() : null);

        // Set income statement values
        income.setEbit(ebit != null ? ebit.toPlainString() : null);
        income.setRevenue(totalRevenue != null ? totalRevenue.toPlainString() : null);

        // Set company overview
        overview.setMarketCap(marketCap != null ? marketCap.toPlainString() : null);

        return ParsedFinancialData.parse(overview, income, balance, cashFlow);
    }

    // ==================== Working Capital Tests ====================

    @Test
    @DisplayName("Should calculate working capital correctly")
    void calculateWorkingCapital_withValidData() {
        // Given
        // currentLiabilities = 2000 + 3000 + 1000 + 0 = 6000
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),   // totalCurrentAssets
            new BigDecimal("2000"),    // shortTermDebt
            new BigDecimal("3000"),    // accountsPayable
            new BigDecimal("1000"),    // otherCurrentLiabilities
            null,                      // deferredRevenue
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 10000 - 6000 = 4000
        assertThat(ratios.getWorkingCapital()).isEqualByComparingTo(new BigDecimal("4000"));
    }

    @Test
    @DisplayName("Should calculate negative working capital correctly")
    void calculateWorkingCapital_withNegativeResult() {
        // Given
        // currentLiabilities = 5000 + 3000 = 8000
        ParsedFinancialData data = createData(
            new BigDecimal("5000"),    // totalCurrentAssets
            new BigDecimal("5000"),    // shortTermDebt
            new BigDecimal("3000"),    // accountsPayable
            null,
            null,
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 5000 - 8000 = -3000
        assertThat(ratios.getWorkingCapital()).isEqualByComparingTo(new BigDecimal("-3000"));
    }

    @Test
    @DisplayName("Should calculate working capital when totalCurrentAssets is null (SafeParser converts to ZERO)")
    void calculateWorkingCapital_withNullAssets() {
        // Given - SafeParser converts null totalCurrentAssets to ZERO
        // currentLiabilities = 2000
        ParsedFinancialData data = createData(
            null,                      // totalCurrentAssets -> ZERO
            new BigDecimal("2000"),
            null,
            null,
            null,
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0 - 2000 = -2000
        assertThat(ratios.getWorkingCapital()).isEqualByComparingTo(new BigDecimal("-2000"));
    }

    @Test
    @DisplayName("Should calculate working capital when currentLiabilities is null (SafeParser converts to ZERO)")
    void calculateWorkingCapital_withNullLiabilities() {
        // Given - SafeParser converts null liabilities to ZERO
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            null,                      // all liability components -> ZERO
            null,
            null,
            null,
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 10000 - 0 = 10000
        assertThat(ratios.getWorkingCapital()).isEqualByComparingTo(new BigDecimal("10000"));
    }

    // ==================== Altman Z-Score Tests ====================

    @Test
    @DisplayName("Should calculate Altman Z-Score correctly with all data available")
    void calculateAltmanZScore_withValidData() {
        // Given
        // Working Capital = 10000 - (2000+3000+1000) = 4000
        // X1 = 4000/50000 = 0.08
        // X2 = 15000/50000 = 0.3
        // X3 = 10000/50000 = 0.2
        // X4 = 100000/(2000+3000+1000+10000) = 100000/16000 = 6.25 (using market cap)
        // X5 = 80000/50000 = 1.6
        // Z = (0.08*1.2) + (0.3*1.4) + (0.2*3.3) + (6.25*0.6) + (1.6*1.0)
        // Z = 0.096 + 0.42 + 0.66 + 3.75 + 1.6 = 6.526
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),   // totalCurrentAssets
            new BigDecimal("2000"),    // shortTermDebt
            new BigDecimal("3000"),    // accountsPayable
            new BigDecimal("1000"),    // otherCurrentLiabilities
            null,
            new BigDecimal("50000"),   // totalAssets
            new BigDecimal("16000"),   // totalLiabilities
            new BigDecimal("40000"),   // totalShareholderEquity (not used since marketCap > 0)
            new BigDecimal("15000"),   // retainedEarnings
            new BigDecimal("10000"),   // ebit
            new BigDecimal("80000"),   // totalRevenue
            new BigDecimal("100000")   // marketCap (used for equity value)
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getAltmanZScore()).isEqualByComparingTo(new BigDecimal("6.5260"));
    }

    @Test
    @DisplayName("Should calculate Altman Z-Score using shareholder equity when market cap is null")
    void calculateAltmanZScore_withNullMarketCap() {
        // Given
        // X4 = 40000/16000 = 2.5 (using shareholder equity)
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            new BigDecimal("1000"),
            null,
            new BigDecimal("50000"),
            new BigDecimal("16000"),
            new BigDecimal("40000"),   // totalShareholderEquity
            new BigDecimal("15000"),
            new BigDecimal("10000"),
            new BigDecimal("80000"),
            null                       // marketCap null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - X4 = 2.5 instead of 6.25
        // Z = 0.096 + 0.42 + 0.66 + (2.5*0.6) + 1.6 = 4.276
        assertThat(ratios.getAltmanZScore()).isEqualByComparingTo(new BigDecimal("4.2760"));
    }

    @Test
    @DisplayName("Should calculate Altman Z-Score using shareholder equity when market cap is zero")
    void calculateAltmanZScore_withZeroMarketCap() {
        // Given
        // X4 = 40000/16000 = 2.5 (using shareholder equity since marketCap is 0)
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            new BigDecimal("1000"),
            null,
            new BigDecimal("50000"),
            new BigDecimal("16000"),
            new BigDecimal("40000"),
            new BigDecimal("15000"),
            new BigDecimal("10000"),
            new BigDecimal("80000"),
            BigDecimal.ZERO            // marketCap is zero
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getAltmanZScore()).isEqualByComparingTo(new BigDecimal("4.2760"));
    }

    @Test
    @DisplayName("Should set Altman Z-Score to null when totalAssets is null")
    void calculateAltmanZScore_withNullTotalAssets() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            new BigDecimal("2000"),
            null,
            null,
            null,
            null,                      // totalAssets null
            new BigDecimal("16000"),
            new BigDecimal("40000"),
            new BigDecimal("15000"),
            new BigDecimal("10000"),
            new BigDecimal("80000"),
            new BigDecimal("100000")
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getAltmanZScore()).isNull();
    }

    @Test
    @DisplayName("Should set Altman Z-Score to null when totalAssets is zero")
    void calculateAltmanZScore_withZeroTotalAssets() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            new BigDecimal("2000"),
            null,
            null,
            null,
            BigDecimal.ZERO,           // totalAssets zero
            new BigDecimal("16000"),
            new BigDecimal("40000"),
            new BigDecimal("15000"),
            new BigDecimal("10000"),
            new BigDecimal("80000"),
            new BigDecimal("100000")
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getAltmanZScore()).isNull();
    }

    @Test
    @DisplayName("Should calculate Altman Z-Score when EBIT is null (SafeParser converts to ZERO)")
    void calculateAltmanZScore_withMissingEbit() {
        // Given - EBIT is null, SafeParser converts to ZERO
        // Working Capital = 10000 - 2000 = 8000
        // X1 = 8000/50000 = 0.16
        // X2 = 15000/50000 = 0.3
        // X3 = 0/50000 = 0
        // X4 = 100000/16000 = 6.25
        // X5 = 80000/50000 = 1.6
        // Z = (0.16*1.2) + (0.3*1.4) + (0*3.3) + (6.25*0.6) + (1.6*1.0)
        // Z = 0.192 + 0.42 + 0 + 3.75 + 1.6 = 5.962
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            new BigDecimal("2000"),
            null,
            null,
            null,
            new BigDecimal("50000"),
            new BigDecimal("16000"),
            new BigDecimal("40000"),
            new BigDecimal("15000"),
            null,                      // ebit null -> ZERO
            new BigDecimal("80000"),
            new BigDecimal("100000")
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getAltmanZScore()).isEqualByComparingTo(new BigDecimal("5.9620"));
    }

    @Test
    @DisplayName("Should calculate Altman Z-Score with zero retained earnings")
    void calculateAltmanZScore_withZeroRetainedEarnings() {
        // Given - retainedEarnings is null (defaults to ZERO)
        // X2 = 0/50000 = 0
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            new BigDecimal("1000"),
            null,
            new BigDecimal("50000"),
            new BigDecimal("16000"),
            new BigDecimal("40000"),
            null,                      // retainedEarnings null
            new BigDecimal("10000"),
            new BigDecimal("80000"),
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - Z = 0.096 + 0 + 0.66 + 1.5 + 1.6 = 3.856
        assertThat(ratios.getAltmanZScore()).isEqualByComparingTo(new BigDecimal("3.8560"));
    }

    @Test
    @DisplayName("Should calculate Altman Z-Score with zero total liabilities (X4 becomes zero)")
    void calculateAltmanZScore_withZeroLiabilities() {
        // Given - totalLiabilities is 0, so X4 becomes 0
        // Working Capital = 10000 - 0 = 10000
        // X1 = 10000/50000 = 0.2
        // X2 = 15000/50000 = 0.3
        // X3 = 10000/50000 = 0.2
        // X4 = 0 (since totalLiabilities is 0)
        // X5 = 80000/50000 = 1.6
        // Z = (0.2*1.2) + (0.3*1.4) + (0.2*3.3) + (0*0.6) + (1.6*1.0)
        // Z = 0.24 + 0.42 + 0.66 + 0 + 1.6 = 2.92
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            new BigDecimal("0"),       // shortTermDebt
            new BigDecimal("0"),       // accountsPayable
            null,
            null,
            new BigDecimal("50000"),
            BigDecimal.ZERO,           // totalLiabilities
            new BigDecimal("40000"),
            new BigDecimal("15000"),
            new BigDecimal("10000"),
            new BigDecimal("80000"),
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getAltmanZScore()).isEqualByComparingTo(new BigDecimal("2.9200"));
    }

    // ==================== Category Name Test ====================

    @Test
    @DisplayName("Should return correct category name")
    void getCategoryName() {
        assertThat(calculator.getCategoryName()).isEqualTo("Other Metrics");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle all null values - working capital is 0, Altman Z-Score is null (SafeParser behavior)")
    void calculate_withAllNullValues() {
        // Given - all values null, SafeParser converts to ZERO
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // Working capital: 0 - 0 = 0
        assertThat(ratios.getWorkingCapital()).isEqualByComparingTo(BigDecimal.ZERO);
        // Altman Z-Score: totalAssets is null (ZERO), so it's null
        assertThat(ratios.getAltmanZScore()).isNull();
    }

    @Test
    @DisplayName("Should handle very large numbers correctly")
    void calculate_withLargeNumbers() {
        // Given - billions
        // Working Capital = 20B - (5B+3B+2B) = 10B
        // X1 = 10B/100B = 0.1
        // X2 = 30B/100B = 0.3
        // X3 = 15B/100B = 0.15
        // X4 = 200B/50B = 4.0
        // X5 = 150B/100B = 1.5
        // Z = (0.1*1.2) + (0.3*1.4) + (0.15*3.3) + (4.0*0.6) + (1.5*1.0)
        // Z = 0.12 + 0.42 + 0.495 + 2.4 + 1.5 = 4.935
        ParsedFinancialData data = createData(
            new BigDecimal("20000000000"),   // totalCurrentAssets: $20B
            new BigDecimal("5000000000"),    // shortTermDebt: $5B
            new BigDecimal("3000000000"),    // accountsPayable: $3B
            new BigDecimal("2000000000"),    // otherCurrentLiabilities: $2B
            null,
            new BigDecimal("100000000000"),  // totalAssets: $100B
            new BigDecimal("50000000000"),   // totalLiabilities: $50B
            new BigDecimal("60000000000"),   // totalShareholderEquity: $60B
            new BigDecimal("30000000000"),   // retainedEarnings: $30B
            new BigDecimal("15000000000"),   // ebit: $15B
            new BigDecimal("150000000000"),  // totalRevenue: $150B
            new BigDecimal("200000000000")   // marketCap: $200B
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // Working Capital: 20B - 10B = 10B
        assertThat(ratios.getWorkingCapital()).isEqualByComparingTo(new BigDecimal("10000000000"));
        // Altman Z-Score calculated above
        assertThat(ratios.getAltmanZScore()).isEqualByComparingTo(new BigDecimal("4.9350"));
    }

    @Test
    @DisplayName("Should calculate both metrics when partial data is provided (SafeParser converts null to ZERO)")
    void calculate_bothMetricsWithPartialData() {
        // Given - SafeParser converts null EBIT to ZERO
        // Working Capital = 10000 - (2000+3000) = 5000
        // X1 = 5000/50000 = 0.1
        // X2 = 15000/50000 = 0.3
        // X3 = 0/50000 = 0 (EBIT is null -> ZERO)
        // X4 = 100000/16000 = 6.25
        // X5 = 80000/50000 = 1.6
        // Z = (0.1*1.2) + (0.3*1.4) + (0*3.3) + (6.25*0.6) + (1.6*1.0)
        // Z = 0.12 + 0.42 + 0 + 3.75 + 1.6 = 5.89
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),   // totalCurrentAssets
            new BigDecimal("2000"),    // shortTermDebt
            new BigDecimal("3000"),    // accountsPayable
            null,
            null,
            new BigDecimal("50000"),   // totalAssets
            new BigDecimal("16000"),
            new BigDecimal("40000"),
            new BigDecimal("15000"),
            null,                      // ebit null -> ZERO
            new BigDecimal("80000"),
            new BigDecimal("100000")
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // Working Capital: 10000 - 5000 = 5000
        assertThat(ratios.getWorkingCapital()).isEqualByComparingTo(new BigDecimal("5000"));
        // Altman Z-Score calculated with EBIT=0
        assertThat(ratios.getAltmanZScore()).isEqualByComparingTo(new BigDecimal("5.8900"));
    }

    @Test
    @DisplayName("Should use shareholder equity as fallback when both market cap and equity are available but market cap is preferred")
    void calculateAltmanZScore_prefersMarketCapOverEquity() {
        // Given - both marketCap and totalShareholderEquity available
        // Should use marketCap (100000) not totalShareholderEquity (40000)
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            new BigDecimal("1000"),
            null,
            new BigDecimal("50000"),
            new BigDecimal("16000"),
            new BigDecimal("40000"),   // totalShareholderEquity: $40k
            new BigDecimal("15000"),
            new BigDecimal("10000"),
            new BigDecimal("80000"),
            new BigDecimal("100000")   // marketCap: $100k (preferred)
        );

        // When
        calculator.calculate(ratios, data);

        // Then - X4 = 100000/16000 = 6.25 (using marketCap, not equity)
        assertThat(ratios.getAltmanZScore()).isEqualByComparingTo(new BigDecimal("6.5260"));
    }
}
