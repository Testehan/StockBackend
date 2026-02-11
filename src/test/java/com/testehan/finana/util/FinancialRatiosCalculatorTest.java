package com.testehan.finana.util;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.finstatement.BalanceSheetReport;
import com.testehan.finana.model.finstatement.CashFlowReport;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.util.ratio.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class FinancialRatiosCalculatorTest {

    private FinancialRatiosCalculator financialRatiosCalculator;

    @Mock private ProfitabilityRatioCalculator profitabilityCalculator;
    @Mock private LiquidityRatioCalculator liquidityCalculator;
    @Mock private LeverageRatioCalculator leverageCalculator;
    @Mock private EfficiencyRatioCalculator efficiencyCalculator;
    @Mock private CashFlowMetricCalculator cashFlowCalculator;
    @Mock private PerShareMetricCalculator perShareCalculator;
    @Mock private DividendMetricCalculator dividendCalculator;
    @Mock private OtherMetricCalculator otherCalculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        financialRatiosCalculator = new FinancialRatiosCalculator(
            profitabilityCalculator, liquidityCalculator, leverageCalculator,
            efficiencyCalculator, cashFlowCalculator, perShareCalculator,
            dividendCalculator, otherCalculator
        );
    }

    @Test
    void calculateRatios_callsAllCalculators() {
        CompanyOverview overview = new CompanyOverview();
        IncomeReport income = new IncomeReport();
        income.setDate("2023-01-01");
        BalanceSheetReport balance = new BalanceSheetReport();
        CashFlowReport cashFlow = new CashFlowReport();
        BigDecimal stockPrice = BigDecimal.TEN;

        FinancialRatiosReport result = financialRatiosCalculator.calculateRatios(
            overview, income, balance, cashFlow, stockPrice
        );

        assertEquals("2023-01-01", result.getDate());
        verify(profitabilityCalculator).calculate(any(), any());
        verify(liquidityCalculator).calculate(any(), any());
        verify(leverageCalculator).calculate(any(), any());
        verify(efficiencyCalculator).calculate(any(), any());
        verify(cashFlowCalculator).calculate(any(), any());
        verify(perShareCalculator).calculate(any(), any());
        verify(dividendCalculator).calculate(any(), any());
        verify(otherCalculator).calculate(any(), any());
    }
}
