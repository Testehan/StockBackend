package com.testehan.finana.service.valuation.dcf;

import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.ReverseDcfOutput;
import com.testehan.finana.model.valuation.dcf.ReverseDcfUserInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ReverseDCFValuationCalculatorTest {

    private ReverseDCFValuationCalculator calculator;
    private DcfCalculationData testData;
    private ReverseDcfUserInput testInput;

    @BeforeEach
    void setUp() {
        calculator = new ReverseDCFValuationCalculator();

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
        testInput = new ReverseDcfUserInput();
        testInput.setDiscountRate(0.09); // 9% WACC
        testInput.setPerpetualGrowthRate(0.02); // 2% perpetual growth
        testInput.setProjectionYears(5);
    }

    @Test
    void testCalculateImpliedGrowthRate_Success() {
        ReverseDcfOutput output = calculator.calculateImpliedGrowthRate(testData, testInput);

        assertNotNull(output);
        assertNotNull(output.impliedFCFGrowthRate());

        // The implied growth rate should be a reasonable value (between -50% and 150%)
        assertTrue(output.impliedFCFGrowthRate() > -0.5);
        assertTrue(output.impliedFCFGrowthRate() < 1.5);
    }

    @Test
    void testCalculateImpliedGrowthRate_InvalidWacc() {
        // WACC less than or equal to perpetual growth rate should return null
        testInput.setDiscountRate(0.02); // Same as perpetual growth rate

        ReverseDcfOutput output = calculator.calculateImpliedGrowthRate(testData, testInput);

        assertNotNull(output);
        assertNull(output.impliedFCFGrowthRate());
    }

    @Test
    void testCalculateImpliedGrowthRate_ZeroBaseFcf() {
        // If base FCF is zero, should return null
        testData = DcfCalculationData.builder()
            .meta(testData.meta())
            .income(testData.income())
            .balanceSheet(testData.balanceSheet())
            .cashFlow(DcfCalculationData.CashFlowData.builder()
                .operatingCashFlow(BigDecimal.ZERO)
                .depreciationAndAmortization(BigDecimal.ZERO)
                .capitalExpenditure(BigDecimal.ZERO)
                .stockBasedCompensation(BigDecimal.ZERO)
                .build())
            .assumptions(testData.assumptions())
            .build();

        ReverseDcfOutput output = calculator.calculateImpliedGrowthRate(testData, testInput);

        assertNotNull(output);
        assertNull(output.impliedFCFGrowthRate());
    }

    @Test
    void testCalculateImpliedGrowthRate_NegativeBaseFcf() {
        // If base FCF is negative, should return null
        testData = DcfCalculationData.builder()
            .meta(testData.meta())
            .income(testData.income())
            .balanceSheet(testData.balanceSheet())
            .cashFlow(DcfCalculationData.CashFlowData.builder()
                .operatingCashFlow(new BigDecimal("5000000000"))
                .depreciationAndAmortization(new BigDecimal("1000000000"))
                .capitalExpenditure(new BigDecimal("-10000000000")) // High CapEx
                .stockBasedCompensation(new BigDecimal("1000000000"))
                .build())
            .assumptions(testData.assumptions())
            .build();

        ReverseDcfOutput output = calculator.calculateImpliedGrowthRate(testData, testInput);

        assertNotNull(output);
        assertNull(output.impliedFCFGrowthRate());
    }

    @Test
    void testCalculateImpliedGrowthRate_DifferentProjectionYears() {
        // Test with different projection years
        testInput.setProjectionYears(10);

        ReverseDcfOutput output = calculator.calculateImpliedGrowthRate(testData, testInput);

        assertNotNull(output);
        assertNotNull(output.impliedFCFGrowthRate());
        
        // Should still be within reasonable bounds
        assertTrue(output.impliedFCFGrowthRate() > -0.5);
        assertTrue(output.impliedFCFGrowthRate() < 1.5);
    }

    @Test
    void testCalculateImpliedGrowthRate_HighPerpetualGrowth() {
        // Test with higher perpetual growth rate
        testInput.setPerpetualGrowthRate(0.03);
        testInput.setDiscountRate(0.10); // Make sure WACC > perpetual growth

        ReverseDcfOutput output = calculator.calculateImpliedGrowthRate(testData, testInput);

        assertNotNull(output);
        assertNotNull(output.impliedFCFGrowthRate());
    }
}
