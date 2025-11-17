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
        assertTrue(output.verdict().contains("VALUED"));
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
}
