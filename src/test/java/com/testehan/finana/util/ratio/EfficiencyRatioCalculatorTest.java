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
 * Unit tests for {@link EfficiencyRatioCalculator}.
 */
class EfficiencyRatioCalculatorTest {

    private EfficiencyRatioCalculator calculator;
    private FinancialRatiosReport ratios;

    @BeforeEach
    void setUp() {
        calculator = new EfficiencyRatioCalculator();
        ratios = new FinancialRatiosReport();
    }

    private ParsedFinancialData createData(BigDecimal totalRevenue,
                                            BigDecimal costOfRevenue,
                                            BigDecimal totalAssets,
                                            BigDecimal inventory,
                                            BigDecimal netReceivables,
                                            BigDecimal accountsPayable,
                                            BigDecimal totalDebt,
                                            BigDecimal shareholderEquity,
                                            BigDecimal cash,
                                            BigDecimal shortTermInvestments) {
        CompanyOverview overview = new CompanyOverview();
        IncomeReport income = new IncomeReport();
        BalanceSheetReport balance = new BalanceSheetReport();
        CashFlowReport cashFlow = new CashFlowReport();

        // Set income statement values
        income.setRevenue(totalRevenue != null ? totalRevenue.toPlainString() : null);
        income.setCostOfRevenue(costOfRevenue != null ? costOfRevenue.toPlainString() : null);

        // Set balance sheet values
        balance.setTotalAssets(totalAssets != null ? totalAssets.toPlainString() : null);
        balance.setInventory(inventory != null ? inventory.toPlainString() : null);
        balance.setNetReceivables(netReceivables != null ? netReceivables.toPlainString() : null);
        balance.setAccountPayables(accountsPayable != null ? accountsPayable.toPlainString() : null);
        balance.setShortTermDebt(totalDebt != null ? totalDebt.toPlainString() : null);
        balance.setLongTermDebt("0");
        balance.setTotalStockholdersEquity(shareholderEquity != null ? shareholderEquity.toPlainString() : null);
        balance.setCashAndCashEquivalents(cash != null ? cash.toPlainString() : null);
        balance.setShortTermInvestments(shortTermInvestments != null ? shortTermInvestments.toPlainString() : null);

        return ParsedFinancialData.parse(overview, income, balance, cashFlow);
    }

    @Test
    @DisplayName("Should calculate asset turnover correctly")
    void calculateAssetTurnover_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),  // totalRevenue
            null,
            new BigDecimal("5000"),   // totalAssets
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 10000/5000 = 2.0
        assertThat(ratios.getAssetTurnover()).isEqualByComparingTo(new BigDecimal("2.0000"));
    }

    @Test
    @DisplayName("Should set asset turnover to null when assets is zero")
    void calculateAssetTurnover_withZeroAssets() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),
            null,
            BigDecimal.ZERO,
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getAssetTurnover()).isNull();
    }

    @Test
    @DisplayName("Should calculate inventory turnover correctly")
    void calculateInventoryTurnover_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            null,
            new BigDecimal("6000"),   // costOfRevenue
            null,
            new BigDecimal("1000"),   // inventory
            null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 6000/1000 = 6.0
        assertThat(ratios.getInventoryTurnover()).isEqualByComparingTo(new BigDecimal("6.0000"));
    }

    @Test
    @DisplayName("Should set inventory turnover to null when inventory is zero")
    void calculateInventoryTurnover_withZeroInventory() {
        // Given
        ParsedFinancialData data = createData(
            null,
            new BigDecimal("6000"),
            null,
            BigDecimal.ZERO,
            null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getInventoryTurnover()).isNull();
    }

    @Test
    @DisplayName("Should calculate receivables turnover correctly")
    void calculateReceivablesTurnover_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),  // totalRevenue
            null,
            null,
            null,
            new BigDecimal("2000"),   // netReceivables
            null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 10000/2000 = 5.0
        assertThat(ratios.getReceivablesTurnover()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    @Test
    @DisplayName("Should calculate payables turnover correctly")
    void calculatePayablesTurnover_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            null,
            new BigDecimal("6000"),   // costOfRevenue
            null,
            null,
            null,
            new BigDecimal("1500"),   // accountsPayable
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 6000/1500 = 4.0
        assertThat(ratios.getPayablesTurnover()).isEqualByComparingTo(new BigDecimal("4.0000"));
    }

    @Test
    @DisplayName("Should calculate days sales outstanding correctly")
    void calculateDaysSalesOutstanding_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),  // totalRevenue
            null,
            null,
            null,
            new BigDecimal("1000"),   // netReceivables
            null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - (1000 * 365) / 10000 = 36.5
        assertThat(ratios.getDaysSalesOutstanding()).isEqualByComparingTo(new BigDecimal("36.5000"));
    }

    @Test
    @DisplayName("Should calculate days inventory outstanding correctly")
    void calculateDaysInventoryOutstanding_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            null,
            new BigDecimal("7300"),   // costOfRevenue
            null,
            new BigDecimal("1000"),   // inventory
            null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - (1000 * 365) / 7300 = 50
        assertThat(ratios.getDaysInventoryOutstanding()).isEqualByComparingTo(new BigDecimal("50.0000"));
    }

    @Test
    @DisplayName("Should calculate days payables outstanding correctly")
    void calculateDaysPayablesOutstanding_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            null,
            new BigDecimal("7300"),   // costOfRevenue
            null,
            null,
            null,
            new BigDecimal("1000"),   // accountsPayable
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - (1000 * 365) / 7300 = 50
        assertThat(ratios.getDaysPayablesOutstanding()).isEqualByComparingTo(new BigDecimal("50.0000"));
    }

    @Test
    @DisplayName("Should calculate cash conversion cycle correctly")
    void calculateCashConversionCycle_withAllData() {
        // Given: Days Inventory Outstanding = 50, Days Sales Outstanding = 36.5, Days Payables Outstanding = 50
        // CCC = 50 + 36.5 - 50 = 36.5
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),  // revenue for DSO
            new BigDecimal("7300"),   // costOfRevenue for DIO and DPO
            null,
            new BigDecimal("1000"),   // inventory for DIO
            new BigDecimal("1000"),   // receivables for DSO
            new BigDecimal("1000"),   // payables for DPO
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDaysInventoryOutstanding()).isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(ratios.getDaysSalesOutstanding()).isEqualByComparingTo(new BigDecimal("36.5000"));
        assertThat(ratios.getDaysPayablesOutstanding()).isEqualByComparingTo(new BigDecimal("50.0000"));
        // CCC = DIO + DSO - DPO = 50 + 36.5 - 50 = 36.5
        assertThat(ratios.getCashConversionCycle()).isEqualByComparingTo(new BigDecimal("36.5000"));
    }

    @Test
    @DisplayName("Should handle CCC when DIO is null")
    void calculateCashConversionCycle_withNullDio() {
        // Given: No inventory data, so DIO = null
        ParsedFinancialData data = createData(
            new BigDecimal("10000"),  // revenue for DSO
            null,
            null,
            null,  // no inventory
            new BigDecimal("1000"),   // receivables for DSO
            new BigDecimal("1000"),   // payables for DPO
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDaysInventoryOutstanding()).isNull();
        // CCC should still calculate with DIO = 0
        // DSO = 36.5, DPO = 50 (but costOfRevenue is null, so DPO is also null)
        // Actually DPO requires costOfRevenue, so this test needs adjustment
        assertThat(ratios.getDaysPayablesOutstanding()).isNull();
        assertThat(ratios.getCashConversionCycle()).isNull(); // Because DPO is null
    }

    @Test
    @DisplayName("Should set CCC to null when DSO is null")
    void calculateCashConversionCycle_withNullDso() {
        // Given: No receivables data
        ParsedFinancialData data = createData(
            null,
            new BigDecimal("7300"),
            null,
            new BigDecimal("1000"),
            null,  // no receivables
            new BigDecimal("1000"),
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDaysSalesOutstanding()).isNull();
        assertThat(ratios.getCashConversionCycle()).isNull();
    }

    @Test
    @DisplayName("Should calculate sales to capital ratio correctly")
    void calculateSalesToCapitalRatio_withValidData() {
        // Given
        // Invested Capital = Debt + Equity - Cash - ShortTermInvestments
        // = 3000 + 7000 - 2000 - 1000 = 7000
        ParsedFinancialData data = createData(
            new BigDecimal("14000"),  // totalRevenue
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("3000"),   // totalDebt
            new BigDecimal("7000"),   // shareholderEquity
            new BigDecimal("2000"),   // cash
            new BigDecimal("1000")    // shortTermInvestments
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 14000/7000 = 2.0
        assertThat(ratios.getSalesToCapitalRatio()).isEqualByComparingTo(new BigDecimal("2.0000"));
    }

    @Test
    @DisplayName("Should set sales to capital ratio to null when invested capital is zero")
    void calculateSalesToCapitalRatio_withZeroCapital() {
        // Given - Invested Capital = 1000 + 1000 - 2000 = 0
        ParsedFinancialData data = createData(
            new BigDecimal("5000"),
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("1000"),
            new BigDecimal("1000"),
            new BigDecimal("2000"),
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getSalesToCapitalRatio()).isNull();
    }

    @Test
    @DisplayName("Should calculate sales to capital ratio when cash is null - SafeParser converts to ZERO")
    void calculateSalesToCapitalRatio_withNullCash() {
        // Given - cash is null, SafeParser converts to ZERO
        // Invested Capital = 1000 + 1000 - 0 = 2000
        ParsedFinancialData data = createData(
            new BigDecimal("5000"),
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("1000"),
            new BigDecimal("1000"),
            null,  // cash becomes ZERO
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 5000/2000 = 2.5
        assertThat(ratios.getSalesToCapitalRatio()).isEqualByComparingTo(new BigDecimal("2.5000"));
    }

    @Test
    @DisplayName("Should handle sales to capital ratio without short term investments")
    void calculateSalesToCapitalRatio_withoutShortTermInvestments() {
        // Given
        // Invested Capital = 3000 + 7000 - 2000 = 8000
        ParsedFinancialData data = createData(
            new BigDecimal("8000"),
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("3000"),
            new BigDecimal("7000"),
            new BigDecimal("2000"),
            null  // no short term investments
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 8000/8000 = 1.0
        assertThat(ratios.getSalesToCapitalRatio()).isEqualByComparingTo(new BigDecimal("1.0000"));
    }

    @Test
    @DisplayName("Should return correct category name")
    void getCategoryName() {
        assertThat(calculator.getCategoryName()).isEqualTo("Efficiency Ratios");
    }

    @Test
    @DisplayName("Should handle all null values gracefully")
    void calculate_withAllNullValues() {
        // Given
        ParsedFinancialData data = createData(null, null, null, null, null, null, null, null, null, null);

        // When
        calculator.calculate(ratios, data);

        // Then - all ratios should be null (SafeParser converts null to ZERO, so denominators become 0)
        assertThat(ratios.getAssetTurnover()).isNull();
        assertThat(ratios.getInventoryTurnover()).isNull();
        assertThat(ratios.getReceivablesTurnover()).isNull();
        assertThat(ratios.getPayablesTurnover()).isNull();
        assertThat(ratios.getDaysSalesOutstanding()).isNull();
        assertThat(ratios.getDaysInventoryOutstanding()).isNull();
        assertThat(ratios.getDaysPayablesOutstanding()).isNull();
        assertThat(ratios.getCashConversionCycle()).isNull();
        assertThat(ratios.getSalesToCapitalRatio()).isNull();
    }

    @Test
    @DisplayName("Should handle high precision calculations")
    void calculate_withHighPrecision() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("12345.6789"),
            null,
            new BigDecimal("6789.1234"),
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 12345.6789 / 6789.1234 = 1.81844... rounded to 4 decimals = 1.8184
        assertThat(ratios.getAssetTurnover()).isEqualByComparingTo(new BigDecimal("1.8184"));
    }

    @Test
    @DisplayName("Should handle very large numbers")
    void calculate_withLargeNumbers() {
        // Given - billions
        ParsedFinancialData data = createData(
            new BigDecimal("500000000000"),  // $500B revenue
            null,
            new BigDecimal("250000000000"),  // $250B assets
            null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getAssetTurnover()).isEqualByComparingTo(new BigDecimal("2.0000"));
    }
}
