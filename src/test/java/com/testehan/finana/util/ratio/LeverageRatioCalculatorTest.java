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
 * Unit tests for {@link LeverageRatioCalculator}.
 */
class LeverageRatioCalculatorTest {

    private LeverageRatioCalculator calculator;
    private FinancialRatiosReport ratios;

    @BeforeEach
    void setUp() {
        calculator = new LeverageRatioCalculator();
        ratios = new FinancialRatiosReport();
    }

    private ParsedFinancialData createData(BigDecimal totalAssets,
                                            BigDecimal totalLiabilities,
                                            BigDecimal totalShareholderEquity,
                                            BigDecimal shortTermDebt,
                                            BigDecimal longTermDebt,
                                            BigDecimal cash,
                                            BigDecimal ebit,
                                            BigDecimal ebitda,
                                            BigDecimal interestExpense) {
        CompanyOverview overview = new CompanyOverview();
        IncomeReport income = new IncomeReport();
        BalanceSheetReport balance = new BalanceSheetReport();
        CashFlowReport cashFlow = new CashFlowReport();

        // Set balance sheet values
        balance.setTotalAssets(totalAssets != null ? totalAssets.toPlainString() : null);
        balance.setTotalLiabilities(totalLiabilities != null ? totalLiabilities.toPlainString() : null);
        balance.setTotalStockholdersEquity(totalShareholderEquity != null ? totalShareholderEquity.toPlainString() : null);
        balance.setShortTermDebt(shortTermDebt != null ? shortTermDebt.toPlainString() : null);
        balance.setLongTermDebt(longTermDebt != null ? longTermDebt.toPlainString() : null);
        balance.setCashAndCashEquivalents(cash != null ? cash.toPlainString() : null);

        // Set income statement values
        income.setEbit(ebit != null ? ebit.toPlainString() : null);
        income.setEbitda(ebitda != null ? ebitda.toPlainString() : null);
        income.setInterestExpense(interestExpense != null ? interestExpense.toPlainString() : null);

        return ParsedFinancialData.parse(overview, income, balance, cashFlow);
    }

    // ==================== Debt to Assets Ratio Tests ====================

    @Test
    @DisplayName("Should calculate debt to assets ratio correctly")
    void calculateDebtToAssetsRatio_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),  // totalAssets
            new BigDecimal("4000"),   // totalLiabilities
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 4000/10000 = 0.4
        assertThat(ratios.getDebtToAssetsRatio()).isEqualByComparingTo(new BigDecimal("0.4000"));
    }

    @Test
    @DisplayName("Should set debt to assets ratio to null when assets is zero")
    void calculateDebtToAssetsRatio_withZeroAssets() {
        // Given
        ParsedFinancialData data = createData(
            BigDecimal.ZERO,          // totalAssets
            new BigDecimal("4000"),   // totalLiabilities
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDebtToAssetsRatio()).isNull();
    }

    @Test
    @DisplayName("Should set debt to assets ratio to null when totalAssets is null")
    void calculateDebtToAssetsRatio_withNullAssets() {
        // Given
        ParsedFinancialData data = createData(
            null,
            new BigDecimal("4000"),
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDebtToAssetsRatio()).isNull();
    }

    @Test
    @DisplayName("Should set debt to assets ratio to zero when totalLiabilities is null (SafeParser converts null to ZERO)")
    void calculateDebtToAssetsRatio_withNullLiabilities() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            null,  // SafeParser converts to ZERO
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/10000 = 0
        assertThat(ratios.getDebtToAssetsRatio()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    // ==================== Debt to Equity Ratio Tests ====================

    @Test
    @DisplayName("Should calculate debt to equity ratio correctly")
    void calculateDebtToEquityRatio_withValidData() {
        // Given
        // totalDebt = shortTermDebt + longTermDebt = 2000 + 3000 = 5000
        ParsedFinancialData data = createData(
            null, null,
            new BigDecimal("10000"),  // totalShareholderEquity
            new BigDecimal("2000"),   // shortTermDebt
            new BigDecimal("3000"),   // longTermDebt
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 5000/10000 = 0.5
        assertThat(ratios.getDebtToEquityRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));
    }

    @Test
    @DisplayName("Should set debt to equity ratio to null when equity is zero")
    void calculateDebtToEquityRatio_withZeroEquity() {
        // Given
        ParsedFinancialData data = createData(
            null, null,
            BigDecimal.ZERO,          // totalShareholderEquity
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDebtToEquityRatio()).isNull();
    }

    @Test
    @DisplayName("Should set debt to equity ratio to null when totalShareholderEquity is null")
    void calculateDebtToEquityRatio_withNullEquity() {
        // Given
        ParsedFinancialData data = createData(
            null, null,
            null,
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDebtToEquityRatio()).isNull();
    }

    @Test
    @DisplayName("Should set debt to equity ratio to zero when totalDebt is null (SafeParser converts null to ZERO)")
    void calculateDebtToEquityRatio_withNullDebt() {
        // Given - no debt set, SafeParser converts null to ZERO
        // totalDebt = 0 + 0 = 0
        ParsedFinancialData data = createData(
            null, null,
            new BigDecimal("10000"),
            null,  // shortTermDebt -> ZERO
            null,  // longTermDebt -> ZERO
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/10000 = 0
        assertThat(ratios.getDebtToEquityRatio()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    // ==================== Interest Coverage Ratio Tests ====================

    @Test
    @DisplayName("Should calculate interest coverage ratio correctly with positive interest expense")
    void calculateInterestCoverageRatio_withPositiveInterestExpense() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null, null, null, null,
            new BigDecimal("5000"),   // ebit
            null,
            new BigDecimal("1000")    // interestExpense (positive)
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 5000/1000 = 5.0
        assertThat(ratios.getInterestCoverageRatio()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    @Test
    @DisplayName("Should calculate interest coverage ratio correctly with negative interest expense")
    void calculateInterestCoverageRatio_withNegativeInterestExpense() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null, null, null, null,
            new BigDecimal("5000"),   // ebit
            null,
            new BigDecimal("-1000")   // interestExpense (negative, should use abs)
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 5000/1000 = 5.0 (absolute value used)
        assertThat(ratios.getInterestCoverageRatio()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    @Test
    @DisplayName("Should set interest coverage ratio to null when interest expense is zero")
    void calculateInterestCoverageRatio_withZeroInterestExpense() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null, null, null, null,
            new BigDecimal("5000"),
            null,
            BigDecimal.ZERO
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getInterestCoverageRatio()).isNull();
    }

    @Test
    @DisplayName("Should set interest coverage ratio to zero when ebit is null (SafeParser converts null to ZERO)")
    void calculateInterestCoverageRatio_withNullEbit() {
        // Given - SafeParser converts null ebit to ZERO
        ParsedFinancialData data = createData(
            null, null, null, null, null, null,
            null,  // ebit -> ZERO
            null,
            new BigDecimal("1000")
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/1000 = 0
        assertThat(ratios.getInterestCoverageRatio()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    @DisplayName("Should set interest coverage ratio to null when interestExpense is null")
    void calculateInterestCoverageRatio_withNullInterestExpense() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null, null, null, null,
            new BigDecimal("5000"),
            null,
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getInterestCoverageRatio()).isNull();
    }

    // ==================== Net Debt to EBITDA Tests ====================

    @Test
    @DisplayName("Should calculate net debt to EBITDA correctly")
    void calculateNetDebtToEbitda_withValidData() {
        // Given
        // netDebt = totalDebt - cash = (2000 + 3000) - 1000 = 4000
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("2000"),   // shortTermDebt
            new BigDecimal("3000"),   // longTermDebt
            new BigDecimal("1000"),   // cash
            null,
            new BigDecimal("2000"),   // ebitda
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 4000/2000 = 2.0
        assertThat(ratios.getNetDebtToEbitda()).isEqualByComparingTo(new BigDecimal("2.0000"));
    }

    @Test
    @DisplayName("Should set net debt to EBITDA to null when EBITDA is zero")
    void calculateNetDebtToEbitda_withZeroEbitda() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            new BigDecimal("1000"),
            null,
            BigDecimal.ZERO,          // ebitda
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getNetDebtToEbitda()).isNull();
    }

    @Test
    @DisplayName("Should calculate net debt to EBITDA when netDebt is effectively zero (no debt)")
    void calculateNetDebtToEbitda_withNullNetDebt() {
        // Given - no debt, SafeParser converts to ZERO
        // totalDebt = 0 + 0 = 0, netDebt = 0 - 0 = 0
        ParsedFinancialData data = createData(
            null, null, null,
            null,   // shortTermDebt -> ZERO
            null,   // longTermDebt -> ZERO
            null,   // cash -> ZERO
            null,
            new BigDecimal("2000"),
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/2000 = 0
        assertThat(ratios.getNetDebtToEbitda()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    @DisplayName("Should set net debt to EBITDA to null when EBITDA is null")
    void calculateNetDebtToEbitda_withNullEbitda() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            new BigDecimal("1000"),
            null,
            null,   // ebitda
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getNetDebtToEbitda()).isNull();
    }

    // ==================== Debt Service Coverage Ratio Tests ====================

    @Test
    @DisplayName("Should calculate debt service coverage ratio correctly with short term debt")
    void calculateDebtServiceCoverageRatio_withValidData() {
        // Given
        // totalDebtService = interestExpense.abs() + shortTermDebt = 500 + 1000 = 1500
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("1000"),   // shortTermDebt
            null,
            null,
            null,
            new BigDecimal("3000"),   // ebitda
            new BigDecimal("-500")    // interestExpense (negative, will use abs)
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 3000/1500 = 2.0
        assertThat(ratios.getDebtServiceCoverageRatio()).isEqualByComparingTo(new BigDecimal("2.0000"));
    }

    @Test
    @DisplayName("Should calculate debt service coverage ratio correctly without short term debt")
    void calculateDebtServiceCoverageRatio_withoutShortTermDebt() {
        // Given
        // totalDebtService = interestExpense.abs() + 0 = 1000
        ParsedFinancialData data = createData(
            null, null, null,
            null,   // no shortTermDebt
            null,
            null,
            null,
            new BigDecimal("5000"),   // ebitda
            new BigDecimal("1000")    // interestExpense
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 5000/1000 = 5.0
        assertThat(ratios.getDebtServiceCoverageRatio()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    @Test
    @DisplayName("Should set debt service coverage ratio to null when total debt service is zero")
    void calculateDebtServiceCoverageRatio_withZeroDebtService() {
        // Given
        // totalDebtService = 0 + 0 = 0
        ParsedFinancialData data = createData(
            null, null, null,
            null,
            null,
            null,
            null,
            new BigDecimal("3000"),
            BigDecimal.ZERO           // interestExpense
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDebtServiceCoverageRatio()).isNull();
    }

    @Test
    @DisplayName("Should set debt service coverage ratio to zero when EBITDA is null (SafeParser converts null to ZERO)")
    void calculateDebtServiceCoverageRatio_withNullEbitda() {
        // Given - SafeParser converts null ebitda to ZERO
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("1000"),
            null,
            null,
            null,
            null,   // ebitda -> ZERO
            new BigDecimal("500")
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/(500+1000) = 0
        assertThat(ratios.getDebtServiceCoverageRatio()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    @DisplayName("Should calculate debt service coverage ratio when interestExpense is null (SafeParser converts to ZERO)")
    void calculateDebtServiceCoverageRatio_withNullInterestExpense() {
        // Given - SafeParser converts null interestExpense to ZERO
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("1000"),
            null,
            null,
            null,
            new BigDecimal("3000"),
            null   // interestExpense -> ZERO
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 3000/(0+1000) = 3.0
        assertThat(ratios.getDebtServiceCoverageRatio()).isEqualByComparingTo(new BigDecimal("3.0000"));
    }

    // ==================== Category Name Test ====================

    @Test
    @DisplayName("Should return correct category name")
    void getCategoryName() {
        assertThat(calculator.getCategoryName()).isEqualTo("Leverage Ratios");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle all null values gracefully")
    void calculate_withAllNullValues() {
        // Given
        ParsedFinancialData data = createData(null, null, null, null, null, null, null, null, null);

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDebtToAssetsRatio()).isNull();
        assertThat(ratios.getDebtToEquityRatio()).isNull();
        assertThat(ratios.getInterestCoverageRatio()).isNull();
        assertThat(ratios.getNetDebtToEbitda()).isNull();
        assertThat(ratios.getDebtServiceCoverageRatio()).isNull();
    }

    @Test
    @DisplayName("Should handle very large numbers correctly")
    void calculate_withLargeNumbers() {
        // Given - billions
        ParsedFinancialData data = createData(
            new BigDecimal("100000000000"),   // totalAssets: $100B
            new BigDecimal("40000000000"),    // totalLiabilities: $40B
            new BigDecimal("60000000000"),    // totalShareholderEquity: $60B
            new BigDecimal("5000000000"),     // shortTermDebt: $5B
            new BigDecimal("15000000000"),    // longTermDebt: $15B
            new BigDecimal("10000000000"),    // cash: $10B
            new BigDecimal("20000000000"),    // ebit: $20B
            new BigDecimal("25000000000"),    // ebitda: $25B
            new BigDecimal("2000000000")      // interestExpense: $2B
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // Debt to Assets: 40B/100B = 0.4
        assertThat(ratios.getDebtToAssetsRatio()).isEqualByComparingTo(new BigDecimal("0.4000"));
        // Debt to Equity: 20B/60B = 0.3333
        assertThat(ratios.getDebtToEquityRatio()).isEqualByComparingTo(new BigDecimal("0.3333"));
        // Interest Coverage: 20B/2B = 10
        assertThat(ratios.getInterestCoverageRatio()).isEqualByComparingTo(new BigDecimal("10.0000"));
        // Net Debt to EBITDA: (20B-10B)/25B = 0.4
        assertThat(ratios.getNetDebtToEbitda()).isEqualByComparingTo(new BigDecimal("0.4000"));
        // Debt Service Coverage: 25B/(2B+5B) = 25/7 = 3.5714
        assertThat(ratios.getDebtServiceCoverageRatio()).isEqualByComparingTo(new BigDecimal("3.5714"));
    }

    @Test
    @DisplayName("Should handle high precision calculations")
    void calculate_withHighPrecision() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("12345.6789"),   // totalAssets
            new BigDecimal("4321.0987"),    // totalLiabilities
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 4321.0987 / 12345.6789 = 0.34999... rounded to 4 decimals = 0.3500
        assertThat(ratios.getDebtToAssetsRatio()).isEqualByComparingTo(new BigDecimal("0.3500"));
    }

    @Test
    @DisplayName("Should calculate all ratios together correctly")
    void calculate_allRatiosTogether() {
        // Given - realistic company data
        ParsedFinancialData data = createData(
            new BigDecimal("50000"),    // totalAssets
            new BigDecimal("30000"),    // totalLiabilities
            new BigDecimal("20000"),    // totalShareholderEquity
            new BigDecimal("5000"),     // shortTermDebt
            new BigDecimal("15000"),    // longTermDebt
            new BigDecimal("8000"),     // cash
            new BigDecimal("12000"),    // ebit
            new BigDecimal("15000"),    // ebitda
            new BigDecimal("2000")      // interestExpense
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // Debt to Assets: 30000/50000 = 0.6
        assertThat(ratios.getDebtToAssetsRatio()).isEqualByComparingTo(new BigDecimal("0.6000"));
        // Debt to Equity: 20000/20000 = 1.0
        assertThat(ratios.getDebtToEquityRatio()).isEqualByComparingTo(new BigDecimal("1.0000"));
        // Interest Coverage: 12000/2000 = 6.0
        assertThat(ratios.getInterestCoverageRatio()).isEqualByComparingTo(new BigDecimal("6.0000"));
        // Net Debt to EBITDA: (20000-8000)/15000 = 0.8
        assertThat(ratios.getNetDebtToEbitda()).isEqualByComparingTo(new BigDecimal("0.8000"));
        // Debt Service Coverage: 15000/(2000+5000) = 2.1429
        assertThat(ratios.getDebtServiceCoverageRatio()).isEqualByComparingTo(new BigDecimal("2.1429"));
    }
}
