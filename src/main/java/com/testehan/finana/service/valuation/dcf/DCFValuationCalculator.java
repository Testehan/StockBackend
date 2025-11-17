package com.testehan.finana.service.valuation.dcf;

import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.DcfOutput;
import com.testehan.finana.model.valuation.dcf.DcfUserInput;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

@Service
public class DCFValuationCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int PROJECTION_YEARS = 5;
    private static final double DEFAULT_TAX_RATE = 0.25;

    public DcfOutput calculateIntrinsicValue(DcfCalculationData data, DcfUserInput input) {
        // Extract inputs
        BigDecimal beta = BigDecimal.valueOf(input.getBeta());
        BigDecimal riskFreeRate = BigDecimal.valueOf(input.getRiskFreeRate());
        BigDecimal marketRiskPremium = BigDecimal.valueOf(input.getMarketRiskPremium());
        BigDecimal fcfGrowthRate = BigDecimal.valueOf(input.getFcfGrowthRate());
        BigDecimal terminalMultiple = BigDecimal.valueOf(input.getTerminalMultiple());
        Boolean sbcAdjustmentToggle = input.getSbcAdjustmentToggle();

        // Extract data from records
        BigDecimal sharesOutstanding = data.meta().sharesOutstanding();
        BigDecimal currentSharePrice = data.meta().currentSharePrice();
        BigDecimal operatingCashFlow = data.cashFlow().operatingCashFlow();
        BigDecimal capitalExpenditure = data.cashFlow().capitalExpenditure();
        BigDecimal stockBasedCompensation = data.cashFlow().stockBasedCompensation();
        BigDecimal totalShortTermDebt = data.balanceSheet().totalShortTermDebt();
        BigDecimal totalLongTermDebt = data.balanceSheet().totalLongTermDebt();
        BigDecimal totalCashAndEquivalents = data.balanceSheet().totalCashAndEquivalents();
        BigDecimal interestExpense = data.income().interestExpense();

        // Apply SBC adjustment if enabled
        BigDecimal initialOperatingCashFlow = operatingCashFlow;
        if (Boolean.TRUE.equals(sbcAdjustmentToggle) && stockBasedCompensation != null) {
            initialOperatingCashFlow = initialOperatingCashFlow.subtract(stockBasedCompensation, MC);
        }

        // --- WACC Calculation ---
        BigDecimal costOfEquity = riskFreeRate.add(beta.multiply(marketRiskPremium, MC), MC);
        BigDecimal totalDebt = totalShortTermDebt.add(totalLongTermDebt, MC);
        BigDecimal marketValueOfEquity = currentSharePrice.multiply(sharesOutstanding, MC);
        BigDecimal marketValueOfDebt = totalDebt;
        BigDecimal totalCapital = marketValueOfEquity.add(marketValueOfDebt, MC);

        BigDecimal costOfDebt = BigDecimal.ZERO;
        if (totalDebt.compareTo(BigDecimal.ZERO) > 0 && interestExpense != null) {
            costOfDebt = interestExpense.divide(totalDebt, MC);
        }

        BigDecimal taxRate = BigDecimal.valueOf(DEFAULT_TAX_RATE);

        BigDecimal wacc = BigDecimal.ZERO;
        if (totalCapital.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal weightOfEquity = marketValueOfEquity.divide(totalCapital, MC);
            BigDecimal weightOfDebt = marketValueOfDebt.divide(totalCapital, MC);
            BigDecimal afterTaxCostOfDebt = costOfDebt.multiply(BigDecimal.ONE.subtract(taxRate, MC), MC);
            wacc = weightOfEquity.multiply(costOfEquity, MC)
                    .add(weightOfDebt.multiply(afterTaxCostOfDebt, MC), MC);
        }

        // --- FCF Projection ---
        List<ProjectedFcf> fcfProjections = new ArrayList<>();
        BigDecimal currentOperatingCashFlow = initialOperatingCashFlow;
        BigDecimal currentCapitalExpenditure = capitalExpenditure;

        for (int i = 1; i <= PROJECTION_YEARS; i++) {
            // Project OCF and CapEx based on the growth rate assumption
            currentOperatingCashFlow = currentOperatingCashFlow.multiply(
                BigDecimal.ONE.add(fcfGrowthRate, MC), MC);
            currentCapitalExpenditure = currentCapitalExpenditure.multiply(
                BigDecimal.ONE.add(fcfGrowthRate, MC), MC);

            // FCFF = OCF - |CapEx|
            BigDecimal fcf = currentOperatingCashFlow.subtract(
                currentCapitalExpenditure.abs(), MC);

            fcfProjections.add(new ProjectedFcf(i, fcf));
        }

        // --- Terminal Value Calculation ---
        BigDecimal terminalValue = BigDecimal.ZERO;
        if (!fcfProjections.isEmpty()) {
            BigDecimal lastProjectedFcf = fcfProjections.get(fcfProjections.size() - 1).fcf();
            terminalValue = lastProjectedFcf.multiply(terminalMultiple, MC);
        }

        // --- Discounting FCFs and Terminal Value ---
        BigDecimal sumOfDiscountedFcfs = BigDecimal.ZERO;
        for (ProjectedFcf proj : fcfProjections) {
            BigDecimal discountFactor = BigDecimal.ONE.add(wacc, MC).pow(proj.year(), MC);
            sumOfDiscountedFcfs = sumOfDiscountedFcfs.add(
                proj.fcf().divide(discountFactor, MC), MC);
        }

        BigDecimal discountedTerminalValue = BigDecimal.ZERO;
        if (wacc.compareTo(BigDecimal.ZERO) >= 0) {
            BigDecimal terminalDiscountFactor = BigDecimal.ONE.add(wacc, MC).pow(PROJECTION_YEARS, MC);
            discountedTerminalValue = terminalValue.divide(terminalDiscountFactor, MC);
        }

        // --- Intrinsic Value Calculation ---
        BigDecimal totalEnterpriseValue = sumOfDiscountedFcfs.add(discountedTerminalValue, MC);

        // Adjust for Cash & Debt to get Equity Value
        BigDecimal equityValue = totalEnterpriseValue
            .add(totalCashAndEquivalents, MC)
            .subtract(totalDebt, MC);

        // Intrinsic Value Per Share
        BigDecimal intrinsicValuePerShare = BigDecimal.ZERO;
        if (sharesOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            intrinsicValuePerShare = equityValue.divide(sharesOutstanding, MC);
        }

        // Determine verdict based on comparison with current share price
        String verdict = determineVerdict(intrinsicValuePerShare, currentSharePrice);

        return DcfOutput.builder()
            .equityValue(equityValue)
            .intrinsicValuePerShare(intrinsicValuePerShare)
            .wacc(wacc.doubleValue())
            .verdict(verdict)
            .build();
    }

    private String determineVerdict(BigDecimal intrinsicValuePerShare, BigDecimal currentSharePrice) {
        if (intrinsicValuePerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return "OVERVALUED - Negative or zero intrinsic value";
        }

        BigDecimal margin = intrinsicValuePerShare.subtract(currentSharePrice, MC)
            .divide(currentSharePrice, MC);
        double marginPercent = margin.doubleValue();

        if (marginPercent > 0.15) {
            return "UNDERVALUED - Potential buy opportunity";
        } else if (marginPercent < -0.15) {
            return "OVERVALUED - Consider selling";
        } else {
            return "FAIRLY VALUED - Within 15% of current price";
        }
    }

    private record ProjectedFcf(int year, BigDecimal fcf) {}
}
