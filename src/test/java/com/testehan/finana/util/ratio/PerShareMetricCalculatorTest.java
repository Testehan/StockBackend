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
 * Unit tests for {@link PerShareMetricCalculator}.
 */
class PerShareMetricCalculatorTest {

    private PerShareMetricCalculator calculator;
    private FinancialRatiosReport ratios;

    @BeforeEach
    void setUp() {
        calculator = new PerShareMetricCalculator();
        ratios = new FinancialRatiosReport();
    }

    private ParsedFinancialData createData(BigDecimal eps,
                                            BigDecimal epsDiluted,
                                            BigDecimal totalShareholderEquity,
                                            BigDecimal shortTermDebt,
                                            BigDecimal longTermDebt,
                                            BigDecimal goodwill,
                                            BigDecimal intangibleAssets,
                                            BigDecimal totalRevenue,
                                            BigDecimal operatingCashflow,
                                            BigDecimal capitalExpenditures,
                                            BigDecimal cash,
                                            BigDecimal sharesOutstandingBasic,
                                            BigDecimal sharesOutstanding) {
        CompanyOverview overview = new CompanyOverview();
        IncomeReport income = new IncomeReport();
        BalanceSheetReport balance = new BalanceSheetReport();
        CashFlowReport cashFlow = new CashFlowReport();

        // Set income statement values
        income.setEps(eps != null ? eps.toPlainString() : null);
        income.setEpsDiluted(epsDiluted != null ? epsDiluted.toPlainString() : null);
        income.setRevenue(totalRevenue != null ? totalRevenue.toPlainString() : null);
        income.setWeightedAverageShsOut(sharesOutstandingBasic != null ? sharesOutstandingBasic.toPlainString() : null);
        income.setWeightedAverageShsOutDil(sharesOutstanding != null ? sharesOutstanding.toPlainString() : null);

        // Set balance sheet values
        balance.setTotalStockholdersEquity(totalShareholderEquity != null ? totalShareholderEquity.toPlainString() : null);
        balance.setShortTermDebt(shortTermDebt != null ? shortTermDebt.toPlainString() : null);
        balance.setLongTermDebt(longTermDebt != null ? longTermDebt.toPlainString() : null);
        balance.setGoodwill(goodwill != null ? goodwill.toPlainString() : null);
        balance.setIntangibleAssets(intangibleAssets != null ? intangibleAssets.toPlainString() : null);
        balance.setCashAndCashEquivalents(cash != null ? cash.toPlainString() : null);

        // Set cash flow values
        cashFlow.setOperatingCashFlow(operatingCashflow != null ? operatingCashflow.toPlainString() : null);
        cashFlow.setCapitalExpenditure(capitalExpenditures != null ? capitalExpenditures.toPlainString() : null);

        return ParsedFinancialData.parse(overview, income, balance, cashFlow);
    }

    // ==================== EPS Basic Tests ====================

    @Test
    @DisplayName("Should set EPS basic correctly")
    void calculateEpsBasic_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("5.50"),    // eps
            null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getEarningsPerShareBasic()).isEqualByComparingTo(new BigDecimal("5.50"));
    }

    @Test
    @DisplayName("Should set EPS basic to ZERO when not available (SafeParser behavior)")
    void calculateEpsBasic_withNullEps() {
        // Given - SafeParser converts null EPS to ZERO
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - SafeParser converts null to ZERO
        assertThat(ratios.getEarningsPerShareBasic()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ==================== EPS Diluted Tests ====================

    @Test
    @DisplayName("Should set EPS diluted correctly")
    void calculateEpsDiluted_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            null,
            new BigDecimal("5.25"),    // epsDiluted
            null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getEarningsPerShareDiluted()).isEqualByComparingTo(new BigDecimal("5.25"));
    }

    // ==================== Book Value Per Share Tests ====================

    @Test
    @DisplayName("Should calculate book value per share correctly")
    void calculateBookValuePerShare_withValidData() {
        // Given
        // totalShareholderEquity = 100000
        // shares = 10000
        ParsedFinancialData data = createData(
            null, null,
            new BigDecimal("100000"),  // totalShareholderEquity
            null, null, null, null, null, null, null, null,
            new BigDecimal("10000"),   // sharesOutstandingBasic
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 100000/10000 = 10
        assertThat(ratios.getBookValuePerShare()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    @DisplayName("Should use diluted shares when basic shares not available")
    void calculateBookValuePerShare_withDilutedShares() {
        // Given
        ParsedFinancialData data = createData(
            null, null,
            new BigDecimal("100000"),
            null, null, null, null, null, null, null, null,
            null,                      // sharesOutstandingBasic null
            new BigDecimal("10000")    // sharesOutstanding available
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getBookValuePerShare()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    @DisplayName("Should set book value per share to ZERO when shareholder equity is null (SafeParser behavior)")
    void calculateBookValuePerShare_withNullEquity() {
        // Given - SafeParser converts null totalShareholderEquity to ZERO
        ParsedFinancialData data = createData(
            null, null,
            null,                      // totalShareholderEquity -> ZERO
            null, null, null, null, null, null, null, null,
            new BigDecimal("10000"),
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/10000 = 0
        assertThat(ratios.getBookValuePerShare()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    @DisplayName("Should set book value per share to null when shares not available")
    void calculateBookValuePerShare_withNullShares() {
        // Given
        ParsedFinancialData data = createData(
            null, null,
            new BigDecimal("100000"),
            null, null, null, null, null, null, null, null,
            null, null                 // both shares null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getBookValuePerShare()).isNull();
    }

    // ==================== Tangible Book Value Per Share Tests ====================

    @Test
    @DisplayName("Should calculate tangible book value per share correctly")
    void calculateTangibleBookValuePerShare_withValidData() {
        // Given
        // tangibleEquity = totalShareholderEquity - goodwill - intangibleAssets
        // = 100000 - 10000 - 5000 = 85000
        // shares = 10000
        ParsedFinancialData data = createData(
            null, null,
            new BigDecimal("100000"),  // totalShareholderEquity
            null, null,
            new BigDecimal("10000"),   // goodwill
            new BigDecimal("5000"),    // intangibleAssets
            null, null, null, null,
            new BigDecimal("10000"),   // sharesOutstandingBasic
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 85000/10000 = 8.5
        assertThat(ratios.getTangibleBookValuePerShare()).isEqualByComparingTo(new BigDecimal("8.5000"));
    }

    @Test
    @DisplayName("Should calculate tangible book value per share when equity is null (SafeParser converts to ZERO)")
    void calculateTangibleBookValuePerShare_withNullTangibleEquity() {
        // Given - SafeParser converts null totalShareholderEquity to ZERO
        // tangibleEquity = totalShareholderEquity - goodwill - intangibleAssets
        // = 0 - 10000 - 5000 = -15000
        ParsedFinancialData data = createData(
            null, null,
            null,                      // totalShareholderEquity -> ZERO
            null, null,
            new BigDecimal("10000"),   // goodwill
            new BigDecimal("5000"),    // intangibleAssets
            null, null, null, null,
            new BigDecimal("10000"),   // sharesOutstandingBasic
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - -15000/10000 = -1.5
        assertThat(ratios.getTangibleBookValuePerShare()).isEqualByComparingTo(new BigDecimal("-1.5000"));
    }

    // ==================== Sales Per Share Tests ====================

    @Test
    @DisplayName("Should calculate sales per share correctly")
    void calculateSalesPerShare_withValidData() {
        // Given
        // totalRevenue = 500000
        // shares = 10000
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null,
            new BigDecimal("500000"),  // totalRevenue
            null, null, null,
            new BigDecimal("10000"),   // sharesOutstandingBasic
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 500000/10000 = 50
        assertThat(ratios.getSalesPerShare()).isEqualByComparingTo(new BigDecimal("50.0000"));
    }

    @Test
    @DisplayName("Should set sales per share to ZERO when revenue is null (SafeParser behavior)")
    void calculateSalesPerShare_withNullRevenue() {
        // Given - SafeParser converts null to ZERO
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null,
            null,                      // totalRevenue -> ZERO
            null, null, null,
            new BigDecimal("10000"),
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/10000 = 0
        assertThat(ratios.getSalesPerShare()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    // ==================== Free Cash Flow Per Share Tests ====================

    @Test
    @DisplayName("Should calculate free cash flow per share correctly")
    void calculateFreeCashFlowPerShare_withValidData() {
        // Given
        // FCF = operatingCashflow - |capitalExpenditures|
        // = 50000 - 10000 = 40000
        // shares = 10000
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null,
            new BigDecimal("50000"),   // operatingCashflow
            new BigDecimal("10000"),   // capitalExpenditures
            null,
            new BigDecimal("10000"),   // sharesOutstandingBasic
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 40000/10000 = 4
        assertThat(ratios.getFreeCashFlowPerShare()).isEqualByComparingTo(new BigDecimal("4.0000"));
    }

    @Test
    @DisplayName("Should handle negative capital expenditures correctly")
    void calculateFreeCashFlowPerShare_withNegativeCapEx() {
        // Given
        // FCF = 50000 - |-10000| = 50000 - 10000 = 40000
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null,
            new BigDecimal("50000"),
            new BigDecimal("-10000"),  // negative capEx
            null,
            new BigDecimal("10000"),
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - still 4.0 since abs is taken
        assertThat(ratios.getFreeCashFlowPerShare()).isEqualByComparingTo(new BigDecimal("4.0000"));
    }

    @Test
    @DisplayName("Should calculate free cash flow per share when operating cash flow is null (SafeParser converts to ZERO)")
    void calculateFreeCashFlowPerShare_withNullOperatingCashFlow() {
        // Given - SafeParser converts null to ZERO
        // FCF = 0 - |10000| = -10000
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null, null,
            new BigDecimal("10000"),   // capitalExpenditures
            null,
            new BigDecimal("10000"),   // sharesOutstandingBasic
            null                       // sharesOutstanding
        );

        // When
        calculator.calculate(ratios, data);

        // Then - -10000/10000 = -1
        assertThat(ratios.getFreeCashFlowPerShare()).isEqualByComparingTo(new BigDecimal("-1.0000"));
    }

    // ==================== Operating Cash Flow Per Share Tests ====================

    @Test
    @DisplayName("Should calculate operating cash flow per share correctly")
    void calculateOperatingCashFlowPerShare_withValidData() {
        // Given
        // operatingCashflow = 50000
        // shares = 10000
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null,
            new BigDecimal("50000"),   // operatingCashflow
            null,
            null,
            new BigDecimal("10000"),   // sharesOutstandingBasic
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 50000/10000 = 5
        assertThat(ratios.getOperatingCashFlowPerShare()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    // ==================== Cash Per Share Tests ====================

    @Test
    @DisplayName("Should calculate cash per share correctly")
    void calculateCashPerShare_withValidData() {
        // Given
        // cash = 25000
        // shares = 10000
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null, null, null,
            new BigDecimal("25000"),   // cash
            new BigDecimal("10000"),   // sharesOutstandingBasic
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 25000/10000 = 2.5
        assertThat(ratios.getCashPerShare()).isEqualByComparingTo(new BigDecimal("2.5000"));
    }

    @Test
    @DisplayName("Should set cash per share to ZERO when cash is null (SafeParser behavior)")
    void calculateCashPerShare_withNullCash() {
        // Given - SafeParser converts null cash to ZERO
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null, null, null,
            null,                      // cash -> ZERO
            new BigDecimal("10000"),
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 0/10000 = 0
        assertThat(ratios.getCashPerShare()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    // ==================== Category Name Test ====================

    @Test
    @DisplayName("Should return correct category name")
    void getCategoryName() {
        assertThat(calculator.getCategoryName()).isEqualTo("Per Share Metrics");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle all null values - SafeParser converts most to ZERO")
    void calculate_withAllNullValues() {
        // Given - all values null, SafeParser converts to ZERO
        ParsedFinancialData data = createData(
            null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - EPS values go through SafeParser (ZERO), per-share metrics need shares
        assertThat(ratios.getEarningsPerShareBasic()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ratios.getEarningsPerShareDiluted()).isEqualByComparingTo(BigDecimal.ZERO);
        // Per-share metrics need shares, which are ZERO (not > 0), so they're null
        assertThat(ratios.getBookValuePerShare()).isNull();
        assertThat(ratios.getTangibleBookValuePerShare()).isNull();
        assertThat(ratios.getSalesPerShare()).isNull();
        assertThat(ratios.getFreeCashFlowPerShare()).isNull();
        assertThat(ratios.getOperatingCashFlowPerShare()).isNull();
        assertThat(ratios.getCashPerShare()).isNull();
    }

    @Test
    @DisplayName("Should handle very large numbers correctly")
    void calculate_withLargeNumbers() {
        // Given - billions
        // shareholderEquity = $100B
        // shares = 1B
        // cash = $25B
        // revenue = $500B
        ParsedFinancialData data = createData(
            new BigDecimal("10.50"),   // eps
            new BigDecimal("10.25"),   // epsDiluted
            new BigDecimal("100000000000"),  // totalShareholderEquity: $100B
            new BigDecimal("20000000000"),   // shortTermDebt: $20B
            new BigDecimal("30000000000"),   // longTermDebt: $30B
            new BigDecimal("5000000000"),    // goodwill: $5B
            new BigDecimal("2000000000"),    // intangibleAssets: $2B
            new BigDecimal("500000000000"),  // totalRevenue: $500B
            new BigDecimal("80000000000"),   // operatingCashflow: $80B
            new BigDecimal("20000000000"),   // capitalExpenditures: $20B
            new BigDecimal("25000000000"),   // cash: $25B
            new BigDecimal("1000000000"),    // sharesOutstandingBasic: 1B
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getEarningsPerShareBasic()).isEqualByComparingTo(new BigDecimal("10.50"));
        assertThat(ratios.getEarningsPerShareDiluted()).isEqualByComparingTo(new BigDecimal("10.25"));
        // Book Value: 100B/1B = $100
        assertThat(ratios.getBookValuePerShare()).isEqualByComparingTo(new BigDecimal("100.0000"));
        // Tangible Book Value: (100B-5B-2B)/1B = $93
        assertThat(ratios.getTangibleBookValuePerShare()).isEqualByComparingTo(new BigDecimal("93.0000"));
        // Sales Per Share: 500B/1B = $500
        assertThat(ratios.getSalesPerShare()).isEqualByComparingTo(new BigDecimal("500.0000"));
        // FCF Per Share: (80B-20B)/1B = $60
        assertThat(ratios.getFreeCashFlowPerShare()).isEqualByComparingTo(new BigDecimal("60.0000"));
        // Operating CF Per Share: 80B/1B = $80
        assertThat(ratios.getOperatingCashFlowPerShare()).isEqualByComparingTo(new BigDecimal("80.0000"));
        // Cash Per Share: 25B/1B = $25
        assertThat(ratios.getCashPerShare()).isEqualByComparingTo(new BigDecimal("25.0000"));
    }

    @Test
    @DisplayName("Should handle shares being zero (returns null for per-share calculations)")
    void calculate_withZeroShares() {
        // Given - shares outstanding is zero
        ParsedFinancialData data = createData(
            null, null,
            new BigDecimal("100000"),
            null, null, null, null, null, null, null, null,
            BigDecimal.ZERO,           // sharesOutstandingBasic is zero
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - all per-share metrics that need shares should be null
        assertThat(ratios.getBookValuePerShare()).isNull();
        assertThat(ratios.getTangibleBookValuePerShare()).isNull();
        assertThat(ratios.getSalesPerShare()).isNull();
    }

    @Test
    @DisplayName("Should calculate all metrics together correctly")
    void calculate_allMetricsTogether() {
        // Given - realistic company data
        ParsedFinancialData data = createData(
            new BigDecimal("8.50"),    // eps
            new BigDecimal("8.25"),    // epsDiluted
            new BigDecimal("500000"),  // totalShareholderEquity
            new BigDecimal("50000"),   // shortTermDebt
            new BigDecimal("100000"),  // longTermDebt
            new BigDecimal("25000"),   // goodwill
            new BigDecimal("15000"),   // intangibleAssets
            new BigDecimal("1000000"), // totalRevenue
            new BigDecimal("150000"),  // operatingCashflow
            new BigDecimal("50000"),   // capitalExpenditures
            new BigDecimal("75000"),   // cash
            new BigDecimal("50000"),   // sharesOutstandingBasic
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getEarningsPerShareBasic()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(ratios.getEarningsPerShareDiluted()).isEqualByComparingTo(new BigDecimal("8.25"));
        // Book Value: 500000/50000 = 10
        assertThat(ratios.getBookValuePerShare()).isEqualByComparingTo(new BigDecimal("10.0000"));
        // Tangible Book Value: (500000-25000-15000)/50000 = 460000/50000 = 9.2
        assertThat(ratios.getTangibleBookValuePerShare()).isEqualByComparingTo(new BigDecimal("9.2000"));
        // Sales Per Share: 1000000/50000 = 20
        assertThat(ratios.getSalesPerShare()).isEqualByComparingTo(new BigDecimal("20.0000"));
        // FCF Per Share: (150000-50000)/50000 = 2
        assertThat(ratios.getFreeCashFlowPerShare()).isEqualByComparingTo(new BigDecimal("2.0000"));
        // Operating CF Per Share: 150000/50000 = 3
        assertThat(ratios.getOperatingCashFlowPerShare()).isEqualByComparingTo(new BigDecimal("3.0000"));
        // Cash Per Share: 75000/50000 = 1.5
        assertThat(ratios.getCashPerShare()).isEqualByComparingTo(new BigDecimal("1.5000"));
    }
}
