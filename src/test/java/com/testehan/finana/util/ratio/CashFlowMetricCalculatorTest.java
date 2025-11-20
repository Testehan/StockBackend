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
 * Unit tests for {@link CashFlowMetricCalculator}.
 */
class CashFlowMetricCalculatorTest {

    private CashFlowMetricCalculator calculator;
    private FinancialRatiosReport ratios;

    @BeforeEach
    void setUp() {
        calculator = new CashFlowMetricCalculator();
        ratios = new FinancialRatiosReport();
    }

    private ParsedFinancialData createData(BigDecimal operatingCashflow,
                                            BigDecimal capitalExpenditures,
                                            BigDecimal totalRevenue,
                                            BigDecimal currentLiabilities,
                                            BigDecimal totalDebt) {
        // Create minimal required objects for ParsedFinancialData
        CompanyOverview overview = new CompanyOverview();
        IncomeReport income = new IncomeReport();
        BalanceSheetReport balance = new BalanceSheetReport();
        CashFlowReport cashFlow = new CashFlowReport();
        
        // Set required values
        cashFlow.setOperatingCashFlow(operatingCashflow != null ? operatingCashflow.toPlainString() : null);
        cashFlow.setCapitalExpenditure(capitalExpenditures != null ? capitalExpenditures.toPlainString() : null);
        income.setRevenue(totalRevenue != null ? totalRevenue.toPlainString() : null);
        
        // For current liabilities: shortTermDebt + currentAccountsPayable + otherCurrentLiabilities + deferredRevenue
        // For simplicity in tests, put everything in shortTermDebt
        if (currentLiabilities != null) {
            balance.setShortTermDebt(currentLiabilities.toPlainString());
        }
        
        // For totalDebt: shortTermDebt + longTermDebt
        if (totalDebt != null) {
            balance.setShortTermDebt(totalDebt.toPlainString());
            balance.setLongTermDebt("0");
        }
        
        return ParsedFinancialData.parse(overview, income, balance, cashFlow);
    }

    @Test
    @DisplayName("Should calculate FCF correctly when both OCF and CapEx are positive")
    void calculateFreeCashFlow_withPositiveValues() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000"),  // operatingCashflow
            new BigDecimal("200"),   // capitalExpenditures
            null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getFreeCashFlow()).isEqualByComparingTo(new BigDecimal("800"));
    }

    @Test
    @DisplayName("Should handle negative CapEx by taking absolute value")
    void calculateFreeCashFlow_withNegativeCapEx() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000"),   // operatingCashflow
            new BigDecimal("-200"),   // capitalExpenditures (negative, should become positive)
            null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getFreeCashFlow()).isEqualByComparingTo(new BigDecimal("800"));
    }

    @Test
    @DisplayName("Should calculate FCF as negative when OCF is less than CapEx")
    void calculateFreeCashFlow_withSmallOCF() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("100"),   // operatingCashflow
            new BigDecimal("200"),   // capitalExpenditures
            null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getFreeCashFlow()).isEqualByComparingTo(new BigDecimal("-100"));
    }

    @Test
    @DisplayName("Should calculate FCF margin correctly")
    void calculateFcfMargin_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000"),  // operatingCashflow
            new BigDecimal("200"),   // capitalExpenditures
            new BigDecimal("5000"),  // totalRevenue
            null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - FCF = 800, Revenue = 5000, Margin = 0.16
        assertThat(ratios.getFreeCashflowMargin()).isEqualByComparingTo(new BigDecimal("0.1600"));
    }

    @Test
    @DisplayName("Should set FCF margin to null when revenue is zero")
    void calculateFcfMargin_withZeroRevenue() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000"),  // operatingCashflow
            new BigDecimal("200"),   // capitalExpenditures
            BigDecimal.ZERO,         // totalRevenue
            null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getFreeCashflowMargin()).isNull();
    }

    @Test
    @DisplayName("Should set FCF margin to null when revenue is null (converted to ZERO by SafeParser)")
    void calculateFcfMargin_withNullRevenue() {
        // Given - SafeParser converts null to ZERO
        ParsedFinancialData data = createData(
            new BigDecimal("1000"),  // operatingCashflow
            new BigDecimal("200"),   // capitalExpenditures
            null,                    // totalRevenue (will become ZERO)
            null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - Zero revenue causes null margin
        assertThat(ratios.getFreeCashflowMargin()).isNull();
    }

    @Test
    @DisplayName("Should calculate operating cash flow ratio correctly")
    void calculateOperatingCashFlowRatio_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000"),  // operatingCashflow
            null,
            null,
            new BigDecimal("500"),   // currentLiabilities (via shortTermDebt)
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 1000/500 = 2.0
        assertThat(ratios.getOperatingCashFlowRatio()).isEqualByComparingTo(new BigDecimal("2.0000"));
    }

    @Test
    @DisplayName("Should set OCF ratio to null when current liabilities is zero")
    void calculateOperatingCashFlowRatio_withZeroLiabilities() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000"),  // operatingCashflow
            null,
            null,
            BigDecimal.ZERO,         // currentLiabilities
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getOperatingCashFlowRatio()).isNull();
    }

    @Test
    @DisplayName("Should set OCF ratio to null when OCF is zero (null converted to ZERO)")
    void calculateOperatingCashFlowRatio_withNullOCF() {
        // Given - SafeParser converts null to ZERO
        ParsedFinancialData data = createData(
            null,                    // operatingCashflow (will become ZERO)
            null,
            null,
            new BigDecimal("500"),   // currentLiabilities
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - Zero OCF causes division by zero check to fail
        assertThat(ratios.getOperatingCashFlowRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate cash flow to debt ratio correctly")
    void calculateCashFlowToDebtRatio_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000"),  // operatingCashflow
            null,
            null,
            null,
            new BigDecimal("2000")   // totalDebt
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 1000/2000 = 0.5
        assertThat(ratios.getCashFlowToDebtRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));
    }

    @Test
    @DisplayName("Should set cash flow to debt ratio to null when total debt is zero")
    void calculateCashFlowToDebtRatio_withZeroDebt() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000"),  // operatingCashflow
            null,
            null,
            null,
            BigDecimal.ZERO          // totalDebt
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getCashFlowToDebtRatio()).isNull();
    }

    @Test
    @DisplayName("Should calculate cash flow to debt ratio as ZERO when OCF is null (converted to ZERO)")
    void calculateCashFlowToDebtRatio_withNullOCF() {
        // Given - SafeParser converts null to ZERO
        ParsedFinancialData data = createData(
            null,                    // operatingCashflow (will become ZERO)
            null,
            null,
            null,
            new BigDecimal("2000")   // totalDebt
        );

        // When
        calculator.calculate(ratios, data);

        // Then - Zero OCF divided by debt = ZERO
        assertThat(ratios.getCashFlowToDebtRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should return correct category name")
    void getCategoryName() {
        assertThat(calculator.getCategoryName()).isEqualTo("Cash Flow Metrics");
    }

    @Test
    @DisplayName("Should handle all null values - SafeParser converts null to ZERO")
    void calculate_withAllNullValues() {
        // Given - all fields are null, SafeParser converts to ZERO
        ParsedFinancialData data = createData(null, null, null, null, null);

        // When
        calculator.calculate(ratios, data);

        // Then - with all zeros: FCF = 0-0 = 0, division by zero causes null for ratios
        assertThat(ratios.getFreeCashFlow()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ratios.getFreeCashflowMargin()).isNull(); // 0/0 = null
        assertThat(ratios.getOperatingCashFlowRatio()).isNull(); // 0/0 = null
        assertThat(ratios.getCashFlowToDebtRatio()).isNull(); // 0/0 = null
    }

    @Test
    @DisplayName("Should handle high precision decimal calculations")
    void calculate_withHighPrecision() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1234.5678"),  // operatingCashflow
            new BigDecimal("987.6543"),   // capitalExpenditures
            new BigDecimal("5000.0000"),  // totalRevenue
            null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - FCF = 1234.5678 - 987.6543 = 246.9135
        assertThat(ratios.getFreeCashFlow()).isEqualByComparingTo(new BigDecimal("246.9135"));
        // Margin = 246.9135 / 5000 = 0.0493827 -> rounded to 4 decimal places = 0.0494
        assertThat(ratios.getFreeCashflowMargin()).isEqualByComparingTo(new BigDecimal("0.0494"));
    }

    @Test
    @DisplayName("Should handle very large numbers")
    void calculate_withLargeNumbers() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000000000"),  // 1 billion operatingCashflow
            new BigDecimal("200000000"),   // 200 million capitalExpenditures
            new BigDecimal("5000000000"),  // 5 billion revenue
            null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getFreeCashFlow()).isEqualByComparingTo(new BigDecimal("800000000"));
        assertThat(ratios.getFreeCashflowMargin()).isEqualByComparingTo(new BigDecimal("0.1600"));
    }
}
