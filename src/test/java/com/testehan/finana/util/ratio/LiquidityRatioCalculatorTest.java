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
 * Unit tests for {@link LiquidityRatioCalculator}.
 */
class LiquidityRatioCalculatorTest {

    private LiquidityRatioCalculator calculator;
    private FinancialRatiosReport ratios;

    @BeforeEach
    void setUp() {
        calculator = new LiquidityRatioCalculator();
        ratios = new FinancialRatiosReport();
    }

    private ParsedFinancialData createData(BigDecimal totalCurrentAssets,
                                            BigDecimal inventory,
                                            BigDecimal cash,
                                            BigDecimal shortTermDebt,
                                            BigDecimal accountsPayable,
                                            BigDecimal otherCurrentLiabilities,
                                            BigDecimal deferredRevenue) {
        CompanyOverview overview = new CompanyOverview();
        IncomeReport income = new IncomeReport();
        BalanceSheetReport balance = new BalanceSheetReport();
        CashFlowReport cashFlow = new CashFlowReport();

        // Set balance sheet values
        balance.setTotalCurrentAssets(totalCurrentAssets != null ? totalCurrentAssets.toPlainString() : null);
        balance.setInventory(inventory != null ? inventory.toPlainString() : null);
        balance.setCashAndCashEquivalents(cash != null ? cash.toPlainString() : null);
        balance.setShortTermDebt(shortTermDebt != null ? shortTermDebt.toPlainString() : null);
        balance.setAccountPayables(accountsPayable != null ? accountsPayable.toPlainString() : null);
        balance.setOtherCurrentLiabilities(otherCurrentLiabilities != null ? otherCurrentLiabilities.toPlainString() : null);
        balance.setDeferredRevenue(deferredRevenue != null ? deferredRevenue.toPlainString() : null);

        return ParsedFinancialData.parse(overview, income, balance, cashFlow);
    }

    // ==================== Current Ratio Tests ====================

    @Test
    @DisplayName("Should calculate current ratio correctly")
    void calculateCurrentRatio_withValidData() {
        // Given
        // currentLiabilities = shortTermDebt + accountsPayable + otherCurrentLiabilities + deferredRevenue
        // = 2000 + 3000 + 1000 + 0 = 6000
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),   // totalCurrentAssets
            null,
            null,
            new BigDecimal("2000"),    // shortTermDebt
            new BigDecimal("3000"),    // accountsPayable
            new BigDecimal("1000"),    // otherCurrentLiabilities
            null                       // deferredRevenue
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 10000/6000 = 1.6667
        assertThat(ratios.getCurrentRatio()).isEqualByComparingTo(new BigDecimal("1.6667"));
    }

    @Test
    @DisplayName("Should set current ratio to null when current liabilities is zero")
    void calculateCurrentRatio_withZeroLiabilities() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getCurrentRatio()).isNull();
    }

    @Test
    @DisplayName("Should set current ratio to zero when totalCurrentAssets is null (SafeParser converts null to ZERO)")
    void calculateCurrentRatio_withNullAssets() {
        // Given - SafeParser converts null to ZERO
        ParsedFinancialData data = createData(
            null,  // totalCurrentAssets -> ZERO
            null,
            null,
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/(2000+3000) = 0
        assertThat(ratios.getCurrentRatio()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    // ==================== Quick Ratio Tests ====================

    @Test
    @DisplayName("Should calculate quick ratio using quickAssets when available")
    void calculateQuickRatio_withQuickAssets() {
        // Given
        // quickAssets = totalCurrentAssets - inventory = 10000 - 3000 = 7000
        // currentLiabilities = 2000 + 2000 = 4000
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),   // totalCurrentAssets
            new BigDecimal("3000"),    // inventory
            null,
            new BigDecimal("2000"),    // shortTermDebt
            new BigDecimal("2000"),    // accountsPayable
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 7000/4000 = 1.75
        assertThat(ratios.getQuickRatio()).isEqualByComparingTo(new BigDecimal("1.7500"));
    }

    @Test
    @DisplayName("Should calculate quick ratio using totalCurrentAssets minus inventory when quickAssets not explicitly available")
    void calculateQuickRatio_calculatingFromAssets() {
        // Given
        // currentLiabilities = 5000
        // quickAssets = totalCurrentAssets - inventory = 12000 - 4000 = 8000
        ParsedFinancialData data = createData(
            new BigDecimal("12000"),   // totalCurrentAssets
            new BigDecimal("4000"),    // inventory
            null,
            new BigDecimal("5000"),    // shortTermDebt
            null,
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 8000/5000 = 1.6
        assertThat(ratios.getQuickRatio()).isEqualByComparingTo(new BigDecimal("1.6000"));
    }

    @Test
    @DisplayName("Should calculate quick ratio using totalCurrentAssets when inventory is null")
    void calculateQuickRatio_withoutInventory() {
        // Given
        // currentLiabilities = 4000
        // totalCurrentAssets = 8000 (inventory is null, so quickAssets = totalCurrentAssets)
        ParsedFinancialData data = createData(
            new BigDecimal("8000"),    // totalCurrentAssets
            null,                      // inventory
            null,
            new BigDecimal("4000"),    // shortTermDebt
            null,
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 8000/4000 = 2.0
        assertThat(ratios.getQuickRatio()).isEqualByComparingTo(new BigDecimal("2.0000"));
    }

    @Test
    @DisplayName("Should set quick ratio to null when current liabilities is zero")
    void calculateQuickRatio_withZeroLiabilities() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            new BigDecimal("3000"),
            null,
            BigDecimal.ZERO,
            null,
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getQuickRatio()).isNull();
    }

    @Test
    @DisplayName("Should calculate quick ratio when totalCurrentAssets is null but inventory exists (SafeParser converts to ZERO)")
    void calculateQuickRatio_withNoAssetsData() {
        // Given - totalCurrentAssets is null (ZERO), inventory is 3000
        // quickAssets = totalCurrentAssets - inventory = 0 - 3000 = -3000
        // currentLiabilities = 5000
        ParsedFinancialData data = createData(
            null,   // totalCurrentAssets -> ZERO
            new BigDecimal("3000"),
            null,
            new BigDecimal("5000"),
            null,
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - -3000/5000 = -0.6
        assertThat(ratios.getQuickRatio()).isEqualByComparingTo(new BigDecimal("-0.6000"));
    }

    // ==================== Cash Ratio Tests ====================

    @Test
    @DisplayName("Should calculate cash ratio correctly")
    void calculateCashRatio_withValidData() {
        // Given
        // currentLiabilities = 2000 + 3000 = 5000
        // cash = 2500
        ParsedFinancialData data = createData(
            null,
            null,
            new BigDecimal("2500"),    // cash
            new BigDecimal("2000"),    // shortTermDebt
            new BigDecimal("3000"),    // accountsPayable
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 2500/5000 = 0.5
        assertThat(ratios.getCashRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));
    }

    @Test
    @DisplayName("Should set cash ratio to zero when cash is null (SafeParser converts null to ZERO)")
    void calculateCashRatio_withNullCash() {
        // Given - SafeParser converts null cash to ZERO
        ParsedFinancialData data = createData(
            null,
            null,
            null,   // cash -> ZERO
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/(2000+3000) = 0
        assertThat(ratios.getCashRatio()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    @DisplayName("Should set cash ratio to null when current liabilities is zero")
    void calculateCashRatio_withZeroLiabilities() {
        // Given
        ParsedFinancialData data = createData(
            null,
            null,
            new BigDecimal("2500"),
            BigDecimal.ZERO,
            null,
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getCashRatio()).isNull();
    }

    // ==================== Category Name Test ====================

    @Test
    @DisplayName("Should return correct category name")
    void getCategoryName() {
        assertThat(calculator.getCategoryName()).isEqualTo("Liquidity Ratios");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle all null values gracefully")
    void calculate_withAllNullValues() {
        // Given
        ParsedFinancialData data = createData(null, null, null, null, null, null, null);

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getCurrentRatio()).isNull();
        assertThat(ratios.getQuickRatio()).isNull();
        assertThat(ratios.getCashRatio()).isNull();
    }

    @Test
    @DisplayName("Should handle very large numbers correctly")
    void calculate_withLargeNumbers() {
        // Given - billions
        ParsedFinancialData data = createData(
            new BigDecimal("50000000000"),   // totalCurrentAssets: $50B
            new BigDecimal("15000000000"),   // inventory: $15B
            new BigDecimal("10000000000"),   // cash: $10B
            new BigDecimal("10000000000"),   // shortTermDebt: $10B
            new BigDecimal("10000000000"),   // accountsPayable: $10B
            new BigDecimal("5000000000"),    // otherCurrentLiabilities: $5B
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // currentLiabilities = 10B + 10B + 5B = 25B
        // Current Ratio: 50B/25B = 2.0
        assertThat(ratios.getCurrentRatio()).isEqualByComparingTo(new BigDecimal("2.0000"));
        // Quick Ratio: (50B-15B)/25B = 35B/25B = 1.4
        assertThat(ratios.getQuickRatio()).isEqualByComparingTo(new BigDecimal("1.4000"));
        // Cash Ratio: 10B/25B = 0.4
        assertThat(ratios.getCashRatio()).isEqualByComparingTo(new BigDecimal("0.4000"));
    }

    @Test
    @DisplayName("Should handle high precision calculations")
    void calculate_withHighPrecision() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("12345.6789"),   // totalCurrentAssets
            null,
            null,
            new BigDecimal("5432.1098"),    // shortTermDebt
            null,
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 12345.6789 / 5432.1098 = 2.27273... rounded to 4 decimals = 2.2727
        assertThat(ratios.getCurrentRatio()).isEqualByComparingTo(new BigDecimal("2.2727"));
    }

    @Test
    @DisplayName("Should calculate all ratios together correctly")
    void calculate_allRatiosTogether() {
        // Given - realistic company data
        ParsedFinancialData data = createData(
            new BigDecimal("15000"),    // totalCurrentAssets
            new BigDecimal("5000"),     // inventory
            new BigDecimal("3000"),     // cash
            new BigDecimal("4000"),     // shortTermDebt
            new BigDecimal("3000"),     // accountsPayable
            new BigDecimal("2000"),     // otherCurrentLiabilities
            new BigDecimal("1000")      // deferredRevenue
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // currentLiabilities = 4000 + 3000 + 2000 + 1000 = 10000
        // Current Ratio: 15000/10000 = 1.5
        assertThat(ratios.getCurrentRatio()).isEqualByComparingTo(new BigDecimal("1.5000"));
        // Quick Ratio: (15000-5000)/10000 = 1.0
        assertThat(ratios.getQuickRatio()).isEqualByComparingTo(new BigDecimal("1.0000"));
        // Cash Ratio: 3000/10000 = 0.3
        assertThat(ratios.getCashRatio()).isEqualByComparingTo(new BigDecimal("0.3000"));
    }

    @Test
    @DisplayName("Should handle negative current liabilities edge case (treats as not > 0)")
    void calculate_withNegativeLiabilities() {
        // Given - negative liabilities (edge case, should result in null)
        // This is unlikely in real data, but tests defensive programming
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            null,
            new BigDecimal("5000"),
            new BigDecimal("-2000"),   // negative (unusual)
            new BigDecimal("1000"),
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - currentLiabilities = -2000 + 1000 = -1000, which is not > 0
        assertThat(ratios.getCurrentRatio()).isNull();
        assertThat(ratios.getQuickRatio()).isNull();
        assertThat(ratios.getCashRatio()).isNull();
    }

    @Test
    @DisplayName("Should handle zero inventory correctly (quick ratio equals current ratio)")
    void calculate_quickRatioWithZeroInventory() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),   // totalCurrentAssets
            BigDecimal.ZERO,           // inventory = 0
            null,
            new BigDecimal("5000"),    // shortTermDebt
            null,
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // currentLiabilities = 5000
        // Current Ratio: 10000/5000 = 2.0
        assertThat(ratios.getCurrentRatio()).isEqualByComparingTo(new BigDecimal("2.0000"));
        // Quick Ratio: (10000-0)/5000 = 2.0
        assertThat(ratios.getQuickRatio()).isEqualByComparingTo(new BigDecimal("2.0000"));
    }
}
