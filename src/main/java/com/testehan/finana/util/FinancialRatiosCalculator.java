package com.testehan.finana.util;

import com.testehan.finana.model.*;
import com.testehan.finana.model.finstatement.BalanceSheetReport;
import com.testehan.finana.model.finstatement.CashFlowReport;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.util.data.ParsedFinancialData;
import com.testehan.finana.util.ratio.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class FinancialRatiosCalculator {

    private final List<RatioCalculator> calculators;

    public FinancialRatiosCalculator(
            ProfitabilityRatioCalculator profitabilityCalculator,
            LiquidityRatioCalculator liquidityCalculator,
            LeverageRatioCalculator leverageCalculator,
            EfficiencyRatioCalculator efficiencyCalculator,
            CashFlowMetricCalculator cashFlowCalculator,
            PerShareMetricCalculator perShareCalculator,
            DividendMetricCalculator dividendCalculator,
            OtherMetricCalculator otherCalculator) {
        
        this.calculators = List.of(
            profitabilityCalculator,
            liquidityCalculator,
            leverageCalculator,
            efficiencyCalculator,
            cashFlowCalculator,
            perShareCalculator,
            dividendCalculator,
            otherCalculator
        );
    }

    /**
     * Calculates all financial ratios for the given company data.
     * 
     * @param companyOverview Company overview data including market cap
     * @param incomeReport Income statement data
     * @param balanceSheetReport Balance sheet data
     * @param cashFlowReport Cash flow statement data
     * @param stockPrice Current stock price (used for dividend yield and buyback yield calculations)
     * @return FinancialRatiosReport populated with all calculated ratios
     */
    public FinancialRatiosReport calculateRatios(CompanyOverview companyOverview,
                                                 IncomeReport incomeReport,
                                                 BalanceSheetReport balanceSheetReport,
                                                 CashFlowReport cashFlowReport,
                                                 BigDecimal stockPrice) {
        FinancialRatiosReport ratios = new FinancialRatiosReport();
        ratios.setDate(incomeReport.getDate());

        ParsedFinancialData data = ParsedFinancialData.parse(
            companyOverview, incomeReport, balanceSheetReport, cashFlowReport, stockPrice
        );

        for (RatioCalculator calculator : calculators) {
            calculator.calculate(ratios, data);
        }

        return ratios;
    }
}
