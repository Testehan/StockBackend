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
 * Unit tests for {@link DividendMetricCalculator}.
 */
class DividendMetricCalculatorTest {

    private DividendMetricCalculator calculator;
    private FinancialRatiosReport ratios;

    @BeforeEach
    void setUp() {
        calculator = new DividendMetricCalculator();
        ratios = new FinancialRatiosReport();
    }

    private ParsedFinancialData createData(BigDecimal dividendPayoutCommonStock,
                                            BigDecimal dividendPayout,
                                            BigDecimal netIncome,
                                            BigDecimal sharesOutstandingBasic,
                                            BigDecimal sharesOutstanding,
                                            BigDecimal stockPrice,
                                            BigDecimal commonStockRepurchased,
                                            BigDecimal marketCap) {
        CompanyOverview overview = new CompanyOverview();
        IncomeReport income = new IncomeReport();
        BalanceSheetReport balance = new BalanceSheetReport();
        CashFlowReport cashFlow = new CashFlowReport();

        overview.setMarketCap(marketCap != null ? marketCap.toPlainString() : null);

        income.setWeightedAverageShsOut(sharesOutstandingBasic != null ? sharesOutstandingBasic.toPlainString() : null);
        income.setWeightedAverageShsOutDil(sharesOutstanding != null ? sharesOutstanding.toPlainString() : null);
        income.setNetIncome(netIncome != null ? netIncome.toPlainString() : null);

        cashFlow.setCommonDividendsPaid(dividendPayoutCommonStock != null ? dividendPayoutCommonStock.toPlainString() : null);
        cashFlow.setNetDividendsPaid(dividendPayout != null ? dividendPayout.toPlainString() : null);
        cashFlow.setCommonStockRepurchased(commonStockRepurchased != null ? commonStockRepurchased.toPlainString() : null);

        return ParsedFinancialData.parse(overview, income, balance, cashFlow, stockPrice);
    }

    // ==================== Dividend Per Share Tests ====================

    @Test
    @DisplayName("Should calculate dividend per share correctly with common stock payout")
    void calculateDividendPerShare_withCommonStockPayout() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000000"),  // dividendPayoutCommonStock
            null, null,
            new BigDecimal("50000"),    // sharesOutstandingBasic
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 1000000/50000 = 20
        assertThat(ratios.getDividendPerShare()).isEqualByComparingTo(new BigDecimal("20.0000"));
    }

    @Test
    @DisplayName("Should calculate dividend per share with preferred stock fallback")
    void calculateDividendPerShare_withPreferredStockFallback() {
        // Given
        ParsedFinancialData data = createData(
            null,                        // dividendPayoutCommonStock
            new BigDecimal("800000"),    // dividendPayout
            null,
            new BigDecimal("40000"),
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 800000/40000 = 20
        assertThat(ratios.getDividendPerShare()).isEqualByComparingTo(new BigDecimal("20.0000"));
    }

    @Test
    @DisplayName("Should set dividend per share to null when no dividend data available")
    void calculateDividendPerShare_withNoDividendData() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("50000"),
            null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDividendPerShare()).isNull();
    }

    // ==================== Dividend Yield Tests ====================

    @Test
    @DisplayName("Should calculate dividend yield correctly")
    void calculateDividendYield_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000000"),
            null, null,
            new BigDecimal("50000"),
            null,
            new BigDecimal("50"),       // stockPrice
            null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - Dividend Per Share = 20, Yield = 20/50 = 0.4
        assertThat(ratios.getDividendPerShare()).isEqualByComparingTo(new BigDecimal("20.0000"));
        assertThat(ratios.getDividendYield()).isEqualByComparingTo(new BigDecimal("0.4000"));
    }

    @Test
    @DisplayName("Should set dividend yield to null when stock price is null")
    void calculateDividendYield_withNullStockPrice() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("1000000"),
            null, null,
            new BigDecimal("50000"),
            null,
            null,                       // stockPrice
            null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDividendYield()).isNull();
    }

    @Test
    @DisplayName("Should set dividend yield to null when dividend per share is zero")
    void calculateDividendYield_withZeroDividend() {
        // Given
        ParsedFinancialData data = createData(
            BigDecimal.ZERO,            // no dividend
            null, null,
            new BigDecimal("50000"),
            null,
            new BigDecimal("50"),
            null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDividendYield()).isNull();
    }

    // ==================== Dividend Payout Ratio Tests ====================

    @Test
    @DisplayName("Should calculate dividend payout ratio correctly")
    void calculateDividendPayoutRatio_withValidData() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("2000000"),  // dividendPayoutCommonStock
            null,
            new BigDecimal("8000000"),  // netIncome
            null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - 2000000/8000000 = 0.25
        assertThat(ratios.getDividendPayoutRatio()).isEqualByComparingTo(new BigDecimal("0.2500"));
    }

    @Test
    @DisplayName("Should set dividend payout ratio to null when net income is null")
    void calculateDividendPayoutRatio_withNullNetIncome() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("2000000"),
            null,
            null,                       // netIncome
            null, null, null, null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getDividendPayoutRatio()).isNull();
    }

    // ==================== Buyback Yield Tests ====================

    @Test
    @DisplayName("Should calculate buyback yield using stock price")
    void calculateBuybackYield_withStockPrice() {
        // Given
        // buybackPerShare = 500000/50000 = 10
        // buybackYield = 10/50 = 0.2
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("50000"),
            null,
            new BigDecimal("50"),       // stockPrice
            new BigDecimal("500000"),   // commonStockRepurchased
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getBuybackYield()).isEqualByComparingTo(new BigDecimal("0.2000"));
    }

    @Test
    @DisplayName("Should calculate buyback yield using market cap as fallback")
    void calculateBuybackYield_withMarketCapFallback() {
        // Given
        // buybackYield = 500000/5000000 = 0.1
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("50000"),
            null,
            null,                       // stockPrice
            new BigDecimal("500000"),   // commonStockRepurchased
            new BigDecimal("5000000")   // marketCap
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getBuybackYield()).isEqualByComparingTo(new BigDecimal("0.1000"));
    }

    @Test
    @DisplayName("Should set buyback yield to null when no buyback data available")
    void calculateBuybackYield_withNoBuybackData() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("50000"),
            null,
            new BigDecimal("50"),
            null, null
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getBuybackYield()).isNull();
    }

    @Test
    @DisplayName("Should set buyback yield to null when no valuation data available")
    void calculateBuybackYield_withNoValuationData() {
        // Given
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("50000"),
            null,
            null,                       // stockPrice
            new BigDecimal("500000"),   // buyback
            null                        // marketCap
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        assertThat(ratios.getBuybackYield()).isNull();
    }

    @Test
    @DisplayName("Should calculate buyback yield with negative buyback amount (money leaving company)")
    void calculateBuybackYield_withNegativeBuybackAmount() {
        // Given - commonStockRepurchased is negative (money leaving company)
        // buybackPerShare = |-500000|/50000 = 10
        // buybackYield = 10/50 = 0.2
        ParsedFinancialData data = createData(
            null, null, null,
            new BigDecimal("50000"),
            null,
            new BigDecimal("50"),        // stockPrice
            new BigDecimal("-500000"),   // commonStockRepurchased (negative)
            null
        );

        // When
        calculator.calculate(ratios, data);

        // Then - should use absolute value
        assertThat(ratios.getBuybackYield()).isEqualByComparingTo(new BigDecimal("0.2000"));
    }

    // ==================== Category Name Test ====================

    @Test
    @DisplayName("Should return correct category name")
    void getCategoryName() {
        assertThat(calculator.getCategoryName()).isEqualTo("Dividend Metrics");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle very large numbers correctly")
    void calculate_withLargeNumbers() {
        // Given - billions
        ParsedFinancialData data = createData(
            new BigDecimal("5000000000"),   // dividendPayout: $5B
            null,
            new BigDecimal("20000000000"),  // netIncome: $20B
            new BigDecimal("1000000000"),   // shares: 1B
            null,
            new BigDecimal("100"),          // stockPrice: $100
            new BigDecimal("3000000000"),   // buyback: $3B
            new BigDecimal("100000000000") // marketCap: $100B
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // Dividend Per Share: 5B/1B = $5
        assertThat(ratios.getDividendPerShare()).isEqualByComparingTo(new BigDecimal("5.0000"));
        // Dividend Yield: 5/100 = 0.05
        assertThat(ratios.getDividendYield()).isEqualByComparingTo(new BigDecimal("0.0500"));
        // Payout Ratio: 5B/20B = 0.25
        assertThat(ratios.getDividendPayoutRatio()).isEqualByComparingTo(new BigDecimal("0.2500"));
        // Buyback Yield: 3B/100B = 0.03
        assertThat(ratios.getBuybackYield()).isEqualByComparingTo(new BigDecimal("0.0300"));
    }

    @Test
    @DisplayName("Should calculate all dividend metrics together")
    void calculate_allMetricsTogether() {
        // Given
        ParsedFinancialData data = createData(
            new BigDecimal("2000000"),   // dividendPayoutCommonStock
            null,
            new BigDecimal("10000000"),  // netIncome
            new BigDecimal("100000"),   // sharesOutstandingBasic
            null,
            new BigDecimal("40"),        // stockPrice
            new BigDecimal("500000"),    // commonStockRepurchased
            new BigDecimal("4000000")    // marketCap
        );

        // When
        calculator.calculate(ratios, data);

        // Then
        // Dividend Per Share: 2M/100K = $20
        assertThat(ratios.getDividendPerShare()).isEqualByComparingTo(new BigDecimal("20.0000"));
        // Dividend Yield: 20/40 = 0.5
        assertThat(ratios.getDividendYield()).isEqualByComparingTo(new BigDecimal("0.5000"));
        // Payout Ratio: 2M/10M = 0.2
        assertThat(ratios.getDividendPayoutRatio()).isEqualByComparingTo(new BigDecimal("0.2000"));
        // Buyback Yield: 500K/4M = 0.125 (using market cap fallback)
        assertThat(ratios.getBuybackYield()).isEqualByComparingTo(new BigDecimal("0.1250"));
    }
}
