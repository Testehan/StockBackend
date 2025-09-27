package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CashFlowData;
import com.testehan.finana.model.CashFlowReport;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.repository.CashFlowRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.service.reporting.FerolSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ShareholderFriendlyActivityCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShareholderFriendlyActivityCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final CashFlowRepository cashFlowRepository;
    private final FerolSseService ferolSseService;

    public ShareholderFriendlyActivityCalculator(CompanyOverviewRepository companyOverviewRepository, CashFlowRepository cashFlowRepository, FerolSseService ferolSseService) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CashFlowData> cashflowDataOptional = cashFlowRepository.findBySymbol(ticker);
        Optional<CompanyOverview> companyOverviewOptional = companyOverviewRepository.findBySymbol(ticker);

        if (cashflowDataOptional.isEmpty() || cashflowDataOptional.get().getAnnualReports().isEmpty()) {
            LOGGER.warn("No cashflow data found for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "Shareholder friendly activity analysis is skipped: No data found.");
            return new FerolReportItem("shareholderFriendlyActivity", 0, "No annual cashflow data available.");
        }

        // Get last 5 years of cashflow statements
        List<CashFlowReport> last5yearsCashflowStatements = cashflowDataOptional.get().getAnnualReports().stream()
                .sorted(Comparator.comparing(CashFlowReport::getDate).reversed())
                .limit(5)
                .collect(Collectors.toList());

        if (last5yearsCashflowStatements.isEmpty() || companyOverviewOptional.isEmpty()) {
            ferolSseService.sendSseEvent(sseEmitter, "Shareholder friendly activity analysis is skipped: No data found.");
            return new FerolReportItem("shareholderFriendlyActivity", 0, "No annual cashflow data available.");
        }

        StringBuilder explanation = new StringBuilder();
        DividendAnalysisResult divResult = analyzeDividends(last5yearsCashflowStatements);

        explanation.append(divResult.explanation);
        int totalScore = divResult.score;

        BigDecimal mostRecentStockRepurchase = BigDecimal.ZERO;
        BigDecimal oldestStockRepurchase = BigDecimal.ZERO;

        if (last5yearsCashflowStatements.getFirst().getCommonStockRepurchased() != null) {
            mostRecentStockRepurchase = getAbsValue(last5yearsCashflowStatements.getFirst().getCommonStockRepurchased());
        }

        if (last5yearsCashflowStatements.getLast().getCommonStockRepurchased() != null) {
            oldestStockRepurchase = getAbsValue(last5yearsCashflowStatements.getLast().getCommonStockRepurchased());
        }

        var minimumBuybackSum = getOnePercentOfMarketCap(companyOverviewOptional.get().getMarketCap());
        totalScore = totalScore + calculateBuybackScore(oldestStockRepurchase, mostRecentStockRepurchase, minimumBuybackSum, explanation);

        totalScore = totalScore + calculateDebtRepaymentScore(last5yearsCashflowStatements,getMarketCapAsBigDecimal(companyOverviewOptional.get().getMarketCap()),explanation);

        return new FerolReportItem("shareholderFriendlyActivity", totalScore, explanation.toString());
    }

    /**
     * Calculates buyback score.
     *
     * @param mostRecentStockRepurchaseSum Absolute amount spent on buybacks in the earliest year (e.g., 5 years ago)
     * @param oldestStockRepurchaseSum  Absolute amount spent in the most recent year
     * @param minThreshold       Minimum absolute amount (e.g., $10M) to consider "meaningful" in recent year
     * @return 1 if active/growing buybacks, 0 otherwise
     */
    public static int calculateBuybackScore(
            BigDecimal mostRecentStockRepurchaseSum,
            BigDecimal oldestStockRepurchaseSum,
            BigDecimal minThreshold, StringBuilder explanation) {

        // Handle nulls
        if (oldestStockRepurchaseSum == null || oldestStockRepurchaseSum.compareTo(BigDecimal.ZERO) <= 0) {
            explanation.append("Latest sum used for stock repurchases is " + oldestStockRepurchaseSum + ". ");
            return 0; // No recent buybacks → no point
        }

        if (mostRecentStockRepurchaseSum == null) {
            mostRecentStockRepurchaseSum = BigDecimal.ZERO;
        }

        // Primary rule: Recent year has meaningful buybacks?
        if (oldestStockRepurchaseSum.compareTo(minThreshold) >= 0) {
            explanation.append("Latest sum used for stock repurchases is " + oldestStockRepurchaseSum + " which is higher than the minimum threshold of " + minThreshold.toPlainString());
            return 1;
        }

        // Secondary: Significant increase over the period (even if recent is small)?
        // E.g., went from near-zero to some activity
        if (mostRecentStockRepurchaseSum.compareTo(BigDecimal.ZERO) == 0 &&
                oldestStockRepurchaseSum.compareTo(minThreshold.divide(BigDecimal.valueOf(2))) > 0)
        {
            explanation.append("Stock repurchases have significant increase over the last period " + oldestStockRepurchaseSum + " which is more than half of 0.5% market cap " + minThreshold.toPlainString());
            return 1; // Started a buyback program
        }

        // Or strong growth (e.g., >50% increase and above half threshold)
        if (mostRecentStockRepurchaseSum.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal growthFactor = oldestStockRepurchaseSum.divide(mostRecentStockRepurchaseSum, 4, RoundingMode.HALF_UP);
            if (growthFactor.compareTo(BigDecimal.valueOf(1.5)) > 0 && // >50% increase
                    oldestStockRepurchaseSum.compareTo(minThreshold.divide(BigDecimal.valueOf(2))) > 0)
            {
                explanation.append("> 50% increase in sum used for repurchases in the last few years. ");
                return 1;
            }
        }

        explanation.append("Nothing worth mentioning concerning stock repurchases. 0 points awarded for it. ");
        return 0;
    }

    /**
     * Returns 1% of market cap (i.e., marketCap * 0.01)
     */
    public BigDecimal getOnePercentOfMarketCap(String marketCap) {
        BigDecimal mc = getMarketCapAsBigDecimal(marketCap);
        if (mc.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return mc.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getMarketCapAsBigDecimal(String marketCap) {
        if (marketCap == null || marketCap.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(marketCap);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private record DividendAnalysisResult(int score, String explanation) {}

    private DividendAnalysisResult analyzeDividends(List<CashFlowReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return new DividendAnalysisResult(0, "No data available for dividend analysis. ");
        }

        StringBuilder sb = new StringBuilder();
        boolean checksPassed = true;

        // --- CHECK 1: GROWTH & CONSISTENCY ---
        // We iterate backwards (from newest to oldest) to check year-over-year
        int yearsWithCuts = 0;
        BigDecimal recentDiv = BigDecimal.ZERO;

        // Get newest dividend for reference
        if (reports.get(0).getCommonDividendsPaid() != null) {
            recentDiv = getAbsValue(reports.get(0).getCommonDividendsPaid());
        }

        if (recentDiv.compareTo(BigDecimal.ZERO) == 0) {
            return new DividendAnalysisResult(0, "Company does not currently pay a dividend. ");
        }

        for (int i = 0; i < reports.size() - 1; i++) {
            BigDecimal thisYear = getAbsValue(reports.get(i).getCommonDividendsPaid());
            BigDecimal prevYear = getAbsValue(reports.get(i + 1).getCommonDividendsPaid());

            // If previous year was > 0, check if this year is a cut
            if (prevYear.compareTo(BigDecimal.ZERO) > 0) {
                // Check if cut is more than 5% (tolerance)
                BigDecimal cutThreshold = prevYear.multiply(BigDecimal.valueOf(0.95));

                if (thisYear.compareTo(cutThreshold) < 0) {
                    sb.append(String.format("Dividend cut detected between %s and %s. ",
                            reports.get(i+1).getDate(), reports.get(i).getDate()));
                    yearsWithCuts++;
                }
            }
        }

        if (yearsWithCuts > 1) {
            sb.append("Dividend history is inconsistent with multiple cuts. ");
            checksPassed = false;
        } else {
            sb.append("Dividend history is largely consistent. ");
        }

        // --- CHECK 2: SUSTAINABILITY (Payout Ratio) ---
        // Check only the most recent year to see if the current dividend is safe
        CashFlowReport latest = reports.get(0);
        BigDecimal operatingCashFlow = getAbsValue(latest.getOperatingCashFlow()); // usually positive
        BigDecimal capex = getAbsValue(latest.getCapitalExpenditure()); // usually negative in report, make abs
        BigDecimal dividends = getAbsValue(latest.getCommonDividendsPaid());

        // FCF = OCF - Capex
        BigDecimal freeCashFlow = operatingCashFlow.subtract(capex);

        if (freeCashFlow.compareTo(BigDecimal.ZERO) <= 0) {
            sb.append("Warning: Free Cash Flow is negative, dividends are funded by debt/cash reserves. ");
            // We might not fail them immediately if they have huge cash reserves, but it's a red flag
            // checksPassed = false; // Optional: be strict
        } else {
            // Payout Ratio = Dividends / FCF
            BigDecimal payoutRatio = dividends.divide(freeCashFlow, 2, RoundingMode.HALF_UP);
            sb.append(String.format("Current FCF Payout Ratio is %s%%. ", payoutRatio.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()));

            if (payoutRatio.compareTo(BigDecimal.valueOf(0.90)) > 0) {
                sb.append("Payout ratio is dangerously high (>90%). ");
                checksPassed = false;
            }
        }

        // --- FINAL SCORING ---
        // Compare Oldest vs Newest just for the "Growth" aspect
        BigDecimal oldestDiv = getAbsValue(reports.get(reports.size()-1).getCommonDividendsPaid());

        if (recentDiv.compareTo(oldestDiv) <= 0 && oldestDiv.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("Dividends have not grown overall in the last 5 years. ");
            checksPassed = false;
        }

        return new DividendAnalysisResult(checksPassed ? 1 : 0, sb.toString());
    }

    private BigDecimal getAbsValue(String value) {
        return getSafeBigDecimal(value).abs();
    }

    /**
     * Calculates Debt Repayment score.
     * Score is 1 if the company has net reduced its debt load over the period.
     */
    public int calculateDebtRepaymentScore(
            List<CashFlowReport> reports,
            BigDecimal marketCap,
            StringBuilder explanation)
    {

        if (reports == null || reports.isEmpty()) {
            return 0;
        }

        BigDecimal totalNetDebtFlow = BigDecimal.ZERO;

        // Sum up 'netDebtIssuance' over the available years
        for (CashFlowReport report : reports) {
            // Field: netDebtIssuance
            // Negative means cash left the company to pay debt (Good for this metric)
            // Positive means cash entered the company from borrowing (Bad for this metric)
            BigDecimal annualNetDebt = getSafeBigDecimal(report.getNetDebtIssuance());
            totalNetDebtFlow = totalNetDebtFlow.add(annualNetDebt);
        }

        // If Total Flow is Negative, they are essentially paying down debt
        if (totalNetDebtFlow.compareTo(BigDecimal.ZERO) < 0) {

            BigDecimal totalRepaidAbs = totalNetDebtFlow.abs();

            // Define Threshold: 1% of Market Cap
            // We only award a point if the repayment is "meaningful" relative to company size
            BigDecimal threshold = BigDecimal.ZERO;
            if (marketCap != null && marketCap.compareTo(BigDecimal.ZERO) > 0) {
                threshold = marketCap.multiply(BigDecimal.valueOf(0.01));
            }

            if (totalRepaidAbs.compareTo(threshold) > 0) {
                explanation.append(String.format("Company is actively deleveraging. Net debt reduced by %s (over 1%% of Market Cap). ",
                        totalRepaidAbs.toPlainString()));
                return 1;
            } else {
                explanation.append("Company reduced debt slightly, but amount is insignificant relative to market cap. ");
                return 0;
            }
        }

        // If we are here, totalNetDebtFlow is >= 0 (They increased debt)
        explanation.append("Company has been a net borrower (increased debt load) over the period. Total Net Debt over past 5 years " + totalNetDebtFlow);
        return 0;
    }

    // Helper to avoid NPEs and clean strings
    private BigDecimal getSafeBigDecimal(String value) {
        if (value == null || value.equals("None") || value.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
