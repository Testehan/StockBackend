package com.testehan.finana.service.valuation.dcf;

import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.DcfOutput;
import com.testehan.finana.model.valuation.dcf.DcfUserInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DCFValuationCalculatorTest {

    private DCFValuationCalculator calculator;
    private DcfCalculationData testData;
    private DcfUserInput testInput;

    @BeforeEach
    void setUp() {
        calculator = new DCFValuationCalculator();

        // Setup test data
        testData = DcfCalculationData.builder()
            .meta(DcfCalculationData.CompanyMeta.builder()
                .ticker("AAPL")
                .companyName("Apple Inc")
                .currency("USD")
                .currentSharePrice(new BigDecimal("175.00"))
                .sharesOutstanding(new BigDecimal("15000000000"))
                .build())
            .income(DcfCalculationData.IncomeData.builder()
                .revenue(new BigDecimal("380000000000"))
                .ebit(new BigDecimal("120000000000"))
                .interestExpense(new BigDecimal("3000000000"))
                .incomeTaxExpense(new BigDecimal("25000000000"))
                .build())
            .balanceSheet(DcfCalculationData.BalanceSheetData.builder()
                .totalCashAndEquivalents(new BigDecimal("60000000000"))
                .totalShortTermDebt(new BigDecimal("15000000000"))
                .totalLongTermDebt(new BigDecimal("95000000000"))
                .totalCurrentAssets(new BigDecimal("120000000000"))
                .totalCurrentLiabilities(new BigDecimal("100000000000"))
                .build())
            .cashFlow(DcfCalculationData.CashFlowData.builder()
                .operatingCashFlow(new BigDecimal("110000000000"))
                .depreciationAndAmortization(new BigDecimal("12000000000"))
                .capitalExpenditure(new BigDecimal("-11000000000"))
                .stockBasedCompensation(new BigDecimal("9000000000"))
                .build())
            .assumptions(DcfCalculationData.HistoricalAssumptions.builder()
                .beta(1.2)
                .riskFreeRate(0.042)
                .marketRiskPremium(0.055)
                .effectiveTaxRate(0.21)
                .revenueGrowthCagr3Year(0.08)
                .averageEbitMargin3Year(0.25)
                .fcfGrowthRate(0.05)
                .marketCapToFcfMultiple(25.0)
                .build())
            .build();

        // Setup test input
        testInput = new DcfUserInput();
        testInput.setBeta(1.2);
        testInput.setRiskFreeRate(0.042);
        testInput.setMarketRiskPremium(0.055);
        testInput.setFcfGrowthRate(0.05);
        testInput.setTerminalMultiple(15);
        testInput.setSbcAdjustmentToggle(false);
    }

    @Test
    void testCalculateIntrinsicValue_Success() {
        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertNotNull(output.equityValue());
        assertNotNull(output.intrinsicValuePerShare());
        assertNotNull(output.wacc());
        assertNotNull(output.verdict());

        // Intrinsic value should be positive
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);

        // WACC should be positive
        assertTrue(output.wacc() > 0);

        // Verdict should be one of the expected values
        assertTrue(output.verdict().equals("Undervalued") || output.verdict().equals("Overvalued") || output.verdict().equals("Neutral"));
    }

    @Test
    void testCalculateIntrinsicValue_WithSBCAdjustment() {
        // First calculate without SBC adjustment
        testInput.setSbcAdjustmentToggle(false);
        DcfOutput outputWithoutSBC = calculator.calculateIntrinsicValue(testData, testInput);
        
        // Then calculate with SBC adjustment
        testInput.setSbcAdjustmentToggle(true);
        DcfOutput outputWithSBC = calculator.calculateIntrinsicValue(testData, testInput);

        // With SBC adjustment, intrinsic value should be lower (we subtract SBC from OCF)
        assertTrue(outputWithSBC.intrinsicValuePerShare().compareTo(outputWithoutSBC.intrinsicValuePerShare()) < 0,
            "Intrinsic value with SBC adjustment should be lower than without");
    }

    @Test
    void testCalculateIntrinsicValue_ZeroSharesOutstanding() {
        testData = DcfCalculationData.builder()
            .meta(DcfCalculationData.CompanyMeta.builder()
                .ticker("AAPL")
                .companyName("Apple Inc")
                .currency("USD")
                .currentSharePrice(new BigDecimal("175.00"))
                .sharesOutstanding(BigDecimal.ZERO)
                .build())
            .income(testData.income())
            .balanceSheet(testData.balanceSheet())
            .cashFlow(testData.cashFlow())
            .assumptions(testData.assumptions())
            .build();

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertEquals(BigDecimal.ZERO, output.intrinsicValuePerShare());
    }

    @Test
    void testCalculateIntrinsicValue_NoDebt() {
        testData = DcfCalculationData.builder()
            .meta(testData.meta())
            .income(testData.income())
            .balanceSheet(DcfCalculationData.BalanceSheetData.builder()
                .totalCashAndEquivalents(new BigDecimal("60000000000"))
                .totalShortTermDebt(BigDecimal.ZERO)
                .totalLongTermDebt(BigDecimal.ZERO)
                .totalCurrentAssets(new BigDecimal("120000000000"))
                .totalCurrentLiabilities(new BigDecimal("100000000000"))
                .build())
            .cashFlow(testData.cashFlow())
            .assumptions(testData.assumptions())
            .build();

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testCalculateIntrinsicValue_HighTerminalMultiple() {
        testInput.setTerminalMultiple(30);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testCalculateIntrinsicValue_NegativeGrowthRate() {
        testInput.setFcfGrowthRate(-0.02);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Even with negative growth, should still calculate
        assertNotNull(output.intrinsicValuePerShare());
    }

    @Test
    void testCalculateIntrinsicValue_ZeroGrowthRate() {
        testInput.setFcfGrowthRate(0.0);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertNotNull(output.intrinsicValuePerShare());
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testCalculateIntrinsicValue_HighGrowthRate() {
        testInput.setFcfGrowthRate(0.20);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);
        // Higher growth should lead to higher intrinsic value
    }

    @Test
    void testCalculateIntrinsicValue_LowTerminalMultiple() {
        testInput.setTerminalMultiple(5);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testCalculateIntrinsicValue_ZeroTerminalMultiple() {
        testInput.setTerminalMultiple(0);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Should still return a valid result, just with zero terminal value
        assertNotNull(output.intrinsicValuePerShare());
    }

    @Test
    void testCalculateIntrinsicValue_VerdictUndervalued() {
        // Set very high growth to ensure intrinsic value is much higher than current price
        testInput.setFcfGrowthRate(0.30);
        testInput.setTerminalMultiple(30);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertEquals("Undervalued", output.verdict());
    }

    @Test
    void testCalculateIntrinsicValue_VerdictOvervalued() {
        // Set negative growth to make intrinsic value lower than current price
        testInput.setFcfGrowthRate(-0.10);
        testInput.setTerminalMultiple(5);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertEquals("Overvalued", output.verdict());
    }

    @Test
    void testCalculateIntrinsicValue_VerdictNeutral() {
        // Use high growth and terminal multiple to get a neutral verdict (within 20% margin)
        testInput.setFcfGrowthRate(0.10);
        testInput.setTerminalMultiple(20);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertEquals("Neutral", output.verdict());
    }

    @Test
    void testCalculateIntrinsicValue_HighBeta() {
        testInput.setBeta(2.0);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Higher beta should result in higher WACC, which should lower intrinsic value
        assertTrue(output.wacc() > 0.10); // Should be > 10% with beta of 2.0
    }

    @Test
    void testCalculateIntrinsicValue_LowBeta() {
        testInput.setBeta(0.5);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Lower beta should result in lower WACC
        assertTrue(output.wacc() > 0);
    }

    @Test
    void testCalculateIntrinsicValue_ZeroInterestExpense() {
        testData = DcfCalculationData.builder()
            .meta(testData.meta())
            .income(DcfCalculationData.IncomeData.builder()
                .revenue(new BigDecimal("380000000000"))
                .ebit(new BigDecimal("120000000000"))
                .interestExpense(BigDecimal.ZERO)
                .incomeTaxExpense(new BigDecimal("25000000000"))
                .build())
            .balanceSheet(testData.balanceSheet())
            .cashFlow(testData.cashFlow())
            .assumptions(testData.assumptions())
            .build();

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Should handle zero interest expense gracefully
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testCalculateIntrinsicValue_HighDebtCompany() {
        testData = DcfCalculationData.builder()
            .meta(testData.meta())
            .income(testData.income())
            .balanceSheet(DcfCalculationData.BalanceSheetData.builder()
                .totalCashAndEquivalents(new BigDecimal("10000000000"))
                .totalShortTermDebt(new BigDecimal("50000000000"))
                .totalLongTermDebt(new BigDecimal("150000000000"))
                .totalCurrentAssets(new BigDecimal("30000000000"))
                .totalCurrentLiabilities(new BigDecimal("60000000000"))
                .build())
            .cashFlow(testData.cashFlow())
            .assumptions(testData.assumptions())
            .build();

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Company with high debt should have lower intrinsic value
        assertNotNull(output.intrinsicValuePerShare());
    }

    @Test
    void testCalculateIntrinsicValue_CashRichCompany() {
        testData = DcfCalculationData.builder()
            .meta(testData.meta())
            .income(testData.income())
            .balanceSheet(DcfCalculationData.BalanceSheetData.builder()
                .totalCashAndEquivalents(new BigDecimal("200000000000"))
                .totalShortTermDebt(BigDecimal.ZERO)
                .totalLongTermDebt(BigDecimal.ZERO)
                .totalCurrentAssets(new BigDecimal("250000000000"))
                .totalCurrentLiabilities(new BigDecimal("50000000000"))
                .build())
            .cashFlow(testData.cashFlow())
            .assumptions(testData.assumptions())
            .build();

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Company with lots of cash and no debt - just verify it calculates
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testCalculateIntrinsicValue_NullStockBasedCompensation() {
        testData = DcfCalculationData.builder()
            .meta(testData.meta())
            .income(testData.income())
            .balanceSheet(testData.balanceSheet())
            .cashFlow(DcfCalculationData.CashFlowData.builder()
                .operatingCashFlow(new BigDecimal("110000000000"))
                .depreciationAndAmortization(new BigDecimal("12000000000"))
                .capitalExpenditure(new BigDecimal("-11000000000"))
                .stockBasedCompensation(null)
                .build())
            .assumptions(testData.assumptions())
            .build();

        testInput.setSbcAdjustmentToggle(true);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Should handle null SBC gracefully
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testCalculateIntrinsicValue_NullInterestExpense() {
        testData = DcfCalculationData.builder()
            .meta(testData.meta())
            .income(DcfCalculationData.IncomeData.builder()
                .revenue(new BigDecimal("380000000000"))
                .ebit(new BigDecimal("120000000000"))
                .interestExpense(null)
                .incomeTaxExpense(new BigDecimal("25000000000"))
                .build())
            .balanceSheet(testData.balanceSheet())
            .cashFlow(testData.cashFlow())
            .assumptions(testData.assumptions())
            .build();

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Should handle null interest expense gracefully
        assertNotNull(output.intrinsicValuePerShare());
    }

    @Test
    void testCalculateIntrinsicValue_DifferentRiskFreeRate() {
        testInput.setRiskFreeRate(0.05);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Higher risk-free rate should increase WACC
        assertTrue(output.wacc() > 0.05);
    }

    @Test
    void testCalculateIntrinsicValue_DifferentMarketRiskPremium() {
        testInput.setMarketRiskPremium(0.08);

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        // Higher market risk premium should increase cost of equity
        assertTrue(output.wacc() > 0);
    }

    @Test
    void testCalculateIntrinsicValue_SmallCapCompany() {
        testData = DcfCalculationData.builder()
            .meta(DcfCalculationData.CompanyMeta.builder()
                .ticker("SMALL")
                .companyName("Small Cap Inc")
                .currency("USD")
                .currentSharePrice(new BigDecimal("10.00"))
                .sharesOutstanding(new BigDecimal("100000000"))
                .build())
            .income(testData.income())
            .balanceSheet(testData.balanceSheet())
            .cashFlow(testData.cashFlow())
            .assumptions(testData.assumptions())
            .build();

        DcfOutput output = calculator.calculateIntrinsicValue(testData, testInput);

        assertNotNull(output);
        assertTrue(output.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testCalculateIntrinsicValue_ConsistentResults() {
        // Run the same calculation twice to ensure consistency
        DcfOutput output1 = calculator.calculateIntrinsicValue(testData, testInput);
        DcfOutput output2 = calculator.calculateIntrinsicValue(testData, testInput);

        assertEquals(output1.intrinsicValuePerShare(), output2.intrinsicValuePerShare());
        assertEquals(output1.wacc(), output2.wacc(), 0.0001);
        assertEquals(output1.verdict(), output2.verdict());
    }
}
