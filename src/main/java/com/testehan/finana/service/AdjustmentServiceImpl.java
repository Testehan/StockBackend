package com.testehan.finana.service;

import com.testehan.finana.model.adjustment.FinancialAdjustment;
import com.testehan.finana.model.adjustment.FinancialAdjustmentReport;
import com.testehan.finana.model.finstatement.*;
import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.repository.*;
import com.testehan.finana.util.SafeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AdjustmentServiceImpl implements AdjustmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdjustmentServiceImpl.class);

    private final IncomeStatementRepository incomeStatementRepository;
    private final FinancialAdjustmentRepository financialAdjustmentRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final CompanyDataService companyDataService;
    private final QuoteService quoteService;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final SafeParser safeParser;

    public AdjustmentServiceImpl(IncomeStatementRepository incomeStatementRepository, FinancialAdjustmentRepository financialAdjustmentRepository, BalanceSheetRepository balanceSheetRepository, CashFlowRepository cashFlowRepository, CompanyDataService companyDataService, QuoteService quoteService, FinancialRatiosRepository financialRatiosRepository, SafeParser safeParser) {
        this.incomeStatementRepository = incomeStatementRepository;
        this.financialAdjustmentRepository = financialAdjustmentRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.companyDataService = companyDataService;
        this.quoteService = quoteService;
        this.financialRatiosRepository = financialRatiosRepository;
        this.safeParser = safeParser;
    }

    @Override
    public FinancialAdjustment getFinancialAdjustments(String symbol) {
        BigDecimal price = getLatestStockPrice(symbol);

        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(symbol);
        Optional<BalanceSheetData> balanceSheetDataOptional = balanceSheetRepository.findBySymbol(symbol);
        Optional<CashFlowData> cashFlowDataOptional = cashFlowRepository.findBySymbol(symbol);
        Optional<FinancialRatiosData> financialRatiosDataOptional = financialRatiosRepository.findBySymbol(symbol);
        Optional<com.testehan.finana.model.CompanyOverview> companyOverviewOptional = companyDataService.getCompanyOverview(symbol).blockOptional()
                .flatMap(list -> list.stream().findFirst());

        if (incomeStatementDataOptional.isEmpty() || incomeStatementDataOptional.get().getAnnualReports() == null ||
                balanceSheetDataOptional.isEmpty() || balanceSheetDataOptional.get().getAnnualReports() == null ||
                cashFlowDataOptional.isEmpty() || cashFlowDataOptional.get().getAnnualReports() == null ||
                financialRatiosDataOptional.isEmpty() ||
                companyOverviewOptional.isEmpty()) {
            return new FinancialAdjustment();
        }

        List<IncomeReport> annualIncomeReports = incomeStatementDataOptional.get().getAnnualReports().stream()
                .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                .collect(Collectors.toList());
        List<BalanceSheetReport> annualBalanceSheetReports = balanceSheetDataOptional.get().getAnnualReports();
        List<CashFlowReport> annualCashFlowReports = cashFlowDataOptional.get().getAnnualReports();
        List<FinancialRatiosReport> annualRatiosReports = financialRatiosDataOptional.get().getAnnualReports();

        if (annualIncomeReports.size() < 5) {
            return new FinancialAdjustment();
        }

        String latestAvailableYear = annualIncomeReports.get(0).getDate().substring(0, 4);

        Optional<FinancialAdjustment> existingAdjustmentOpt = financialAdjustmentRepository.findBySymbol(symbol);
        if (existingAdjustmentOpt.isPresent()) {
            FinancialAdjustment existing = existingAdjustmentOpt.get();
            String lastCalculatedYear = existing.getAnnualAdjustments().stream()
                    .map(r -> r.getDate().substring(0, 4))
                    .max(Comparator.naturalOrder())
                    .orElse("");

            // Smart Refresh: If we have a newer year in the repository than what was last calculated
            if (latestAvailableYear.compareTo(lastCalculatedYear) <= 0) {
                if (price != null) {
                    existing.setLastUpdated(LocalDateTime.now());
                    updatePriceDependentMetrics(existing, price);
                }
                return existing;
            }
            // If newer year is available, we fall through and recalculate everything to include the new year(s)
        }

        // Calculate historical adjustments for all available periods that have 5 years of history
        List<FinancialAdjustmentReport> allAdjustments = new ArrayList<>();
        
        // We iterate through available income reports. For each, we need 5 years of history.
        // So we can calculate adjustments for index i where i + 4 < annualIncomeReports.size()
        for (int i = 0; i <= annualIncomeReports.size() - 5; i++) {
            List<IncomeReport> fiveYearWindow = annualIncomeReports.subList(i, i + 5);
            String currentYear = fiveYearWindow.get(0).getDate().substring(0, 4);

            Optional<BalanceSheetReport> bsReport = annualBalanceSheetReports.stream()
                    .filter(r -> r.getDate().startsWith(currentYear)).findFirst();
            Optional<CashFlowReport> cfReport = annualCashFlowReports.stream()
                    .filter(r -> r.getDate().startsWith(currentYear)).findFirst();
            Optional<FinancialRatiosReport> ratioReport = annualRatiosReports.stream()
                    .filter(r -> r.getDate().startsWith(currentYear)).findFirst();

            if (bsReport.isPresent() && cfReport.isPresent() && ratioReport.isPresent()) {
                // Only provide the 'live' price for the very latest year (index 0)
                BigDecimal priceToUse = (i == 0) ? price : null;
                FinancialAdjustmentReport report = calculateRdAdjustment(fiveYearWindow, bsReport.get(), cfReport.get(), ratioReport.get(), companyOverviewOptional.get(), priceToUse);
                if (report.getCalendarYear() > 0) {
                    allAdjustments.add(report);
                }
            }
        }

        FinancialAdjustment financialAdjustment = existingAdjustmentOpt.orElse(new FinancialAdjustment());
        financialAdjustment.setSymbol(symbol);
        financialAdjustment.setLastUpdated(LocalDateTime.now());
        financialAdjustment.setAnnualAdjustments(allAdjustments);

        return financialAdjustmentRepository.save(financialAdjustment);
    }

    private void updatePriceDependentMetrics(FinancialAdjustment adjustment, BigDecimal price) {
        if (adjustment.getAnnualAdjustments() != null && !adjustment.getAnnualAdjustments().isEmpty()) {
            // Only update the latest report, assuming sorting by date DESC (latest first)
            // If not sorted, we should find the latest by date string
            FinancialAdjustmentReport latestReport = adjustment.getAnnualAdjustments().stream()
                    .max(Comparator.comparing(FinancialAdjustmentReport::getDate))
                    .orElse(adjustment.getAnnualAdjustments().get(0));

            // 1. Update Adjusted PE
            BigDecimal adjustedEps = safeParser.parse(latestReport.getAdjustedEps());
            BigDecimal newAdjustedPe = calculateAdjustedPe(price, adjustedEps);
            latestReport.setAdjustedPe(newAdjustedPe.toString());

            // 2. Update Reported PE (using the new price and reported EPS)
            BigDecimal reportedEps = safeParser.parse(latestReport.getReportedEps());
            BigDecimal newReportedPe = calculateAdjustedPe(price, reportedEps);
            latestReport.setReportedPe(newReportedPe.toString());

            // 3. Update EV/EBITDA (requires recalculating EV with the new price)
            BigDecimal shares = safeParser.parse(latestReport.getWeightedAverageShsOutDil());
            BigDecimal debt = safeParser.parse(latestReport.getTotalDebt());
            BigDecimal cash = safeParser.parse(latestReport.getCashAndCashEquivalents());
            BigDecimal adjustedEbitda = safeParser.parse(latestReport.getAdjustedEbitda());
            BigDecimal reportedEbitda = safeParser.parse(latestReport.getReportedEbitda());

            if (shares.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal newMarketCap = price.multiply(shares);
                BigDecimal newEv = calculateEnterpriseValue(newMarketCap, debt, cash);

                BigDecimal newAdjustedEvToEbitda = calculateEvToAdjustedEbitda(newEv, adjustedEbitda);
                latestReport.setAdjustedEvToEbitda(newAdjustedEvToEbitda.toString());

                BigDecimal newReportedEvToEbitda = calculateEvToAdjustedEbitda(newEv, reportedEbitda);
                latestReport.setReportedEvToEbitda(newReportedEvToEbitda.toString());
            }

            financialAdjustmentRepository.save(adjustment);
        }
    }

    @Override
    public void deleteFinancialAdjustmentBySymbol(String symbol) {
        financialAdjustmentRepository.findBySymbol(symbol).ifPresent(financialAdjustmentRepository::delete);
    }

    private FinancialAdjustmentReport calculateRdAdjustment(List<IncomeReport> incomeReports, BalanceSheetReport balanceSheetReport, CashFlowReport cashFlowReport, FinancialRatiosReport ratiosReport, com.testehan.finana.model.CompanyOverview companyOverview, BigDecimal price) {
        FinancialAdjustmentReport report = new FinancialAdjustmentReport();

        // Ensure we have exactly 5 years, sorted in descending order (latest first)
        if (incomeReports.size() < 5) {
            return report;
        }

        IncomeReport year0Income = incomeReports.get(0);
        RdAdjustmentData data = parseFinancialData(incomeReports, balanceSheetReport, cashFlowReport, ratiosReport, companyOverview, price);

        // Check if R&D for latest year is valid and > 0, and other core values
        if (data.rd0.compareTo(BigDecimal.ZERO) <= 0 || data.ebit0.compareTo(BigDecimal.ZERO) == 0 || data.revenue0.compareTo(BigDecimal.ZERO) == 0) {
            return report;
        }

        report.setCalendarYear(Integer.parseInt(year0Income.getDate().substring(0, 4))); // Assuming date is "YYYY-MM-DD"
        report.setDate(year0Income.getDate());

        data.researchAsset = calculateResearchAsset(data.rd0, data.rd_1, data.rd_2, data.rd_3, data.rd_4);

        calculateOperatingAndProfitabilityMetrics(report, data);
        calculateEquityAndEarningsMetrics(report, data);
        calculateCashFlowAndEbitdaMetrics(report, data);
        
        // Custom logic for Valuation metrics based on whether we have a "live" price
        calculateValuationAndLeverageMetrics(report, data);
        
        if (price == null) {
            // If no live price is provided, ensure we use the reported historical ratios
            report.setReportedPe(data.reportedPe != null ? data.reportedPe.toString() : "0");
            report.setReportedEvToEbitda(data.reportedEvToEbitda != null ? data.reportedEvToEbitda.toString() : "0");
            
            // For historical adjusted PE, we don't have the historical price easily, 
            // so we'll just use the reported PE as a placeholder or 0.
            // In a more advanced version, we'd fetch historical prices.
            report.setAdjustedPe("0");
            report.setAdjustedEvToEbitda("0");
        }

        return report;
    }

    private RdAdjustmentData parseFinancialData(List<IncomeReport> incomeReports, BalanceSheetReport balanceSheetReport, CashFlowReport cashFlowReport, FinancialRatiosReport ratiosReport, com.testehan.finana.model.CompanyOverview companyOverview, BigDecimal price) {
        RdAdjustmentData data = new RdAdjustmentData();
        IncomeReport year0Income = incomeReports.get(0);

        data.rd0 = safeParser.parse(year0Income.getResearchAndDevelopmentExpenses());
        data.rd_1 = safeParser.parse(incomeReports.get(1).getResearchAndDevelopmentExpenses());
        data.rd_2 = safeParser.parse(incomeReports.get(2).getResearchAndDevelopmentExpenses());
        data.rd_3 = safeParser.parse(incomeReports.get(3).getResearchAndDevelopmentExpenses());
        data.rd_4 = safeParser.parse(incomeReports.get(4).getResearchAndDevelopmentExpenses());

        data.ebit0 = safeParser.parse(year0Income.getOperatingIncome());
        data.reportedEbitda0 = safeParser.parse(year0Income.getEbitda());
        data.revenue0 = safeParser.parse(year0Income.getRevenue());
        data.incomeTaxExpense0 = safeParser.parse(year0Income.getIncomeTaxExpense());
        data.incomeBeforeTax0 = safeParser.parse(year0Income.getIncomeBeforeTax());
        data.interestExpense0 = safeParser.parse(year0Income.getInterestExpense());
        data.netIncome0 = safeParser.parse(year0Income.getNetIncome());
        data.depreciationAndAmortization0 = safeParser.parse(year0Income.getDepreciationAndAmortization());
        data.otherExpenses0 = safeParser.parse(year0Income.getOtherExpenses());

        data.totalDebt = safeParser.parse(balanceSheetReport.getTotalDebt());
        data.totalEquity = safeParser.parse(balanceSheetReport.getTotalEquity());
        data.cashAndCashEquivalents = safeParser.parse(balanceSheetReport.getCashAndCashEquivalents());
        data.totalStockholdersEquity = safeParser.parse(balanceSheetReport.getTotalStockholdersEquity());

        data.stockBasedCompensation = safeParser.parse(cashFlowReport.getStockBasedCompensation());
        data.operatingCashFlow = safeParser.parse(cashFlowReport.getOperatingCashFlow());
        data.capitalExpenditure = safeParser.parse(cashFlowReport.getCapitalExpenditure());
        data.reportedFreeCashFlow0 = safeParser.parse(cashFlowReport.getFreeCashFlow());

        data.currentSharePrice = price;
        data.marketCap = safeParser.parse(companyOverview.getMarketCap());
        data.weightedAverageShsOutDil = safeParser.parse(year0Income.getWeightedAverageShsOutDil());
        data.reportedEps0 = safeParser.parse(year0Income.getEpsDiluted());

        data.reportedRoic = ratiosReport.getRoic();
        data.reportedPe = ratiosReport.getPeRatio();
        data.reportedNetDebtToEbitda = ratiosReport.getNetDebtToEbitda();
        data.reportedSalesToCapital = ratiosReport.getSalesToCapitalRatio();
        data.reportedInterestCoverage = ratiosReport.getInterestCoverageRatio();
        data.reportedEvToEbitda = ratiosReport.getEnterpriseValueMultiple();

        return data;
    }

    private void calculateOperatingAndProfitabilityMetrics(FinancialAdjustmentReport report, RdAdjustmentData data) {
        BigDecimal rdAmortization = data.rd_4;
        BigDecimal rdCapitalizationAdjustment = calculateRdCapitalizationAdjustment(data.rd0, rdAmortization);
        
        // Damodaran Normalization: Add back non-recurring 'Other Expenses' to Operating Income (EBIT)
        BigDecimal cleanedEbit = data.ebit0.add(data.otherExpenses0);
        data.adjustedOperatingIncome = calculateAdjustedOperatingIncome(cleanedEbit, rdCapitalizationAdjustment);

        report.setAdjustedOperatingIncome(data.adjustedOperatingIncome.toString());
        report.setReportedOperatingIncome(data.ebit0.toString());

        BigDecimal investedCapitalReported = calculateInvestedCapitalReported(data.totalDebt, data.totalEquity, data.cashAndCashEquivalents);
        BigDecimal marginalTaxRate = calculateMarginalTaxRate(data.incomeTaxExpense0, data.incomeBeforeTax0);
        
        // Damodaran Fix: Adjusted Invested Capital = Reported + Research Asset - (Research Asset * Marginal Tax Rate)
        BigDecimal taxBenefit = data.researchAsset.multiply(marginalTaxRate);
        BigDecimal adjInvestedCapital = calculateAdjInvestedCapital(investedCapitalReported, data.researchAsset).subtract(taxBenefit);

        report.setReportedInvestedCapital(investedCapitalReported.toString());
        report.setAdjustedInvestedCapital(adjInvestedCapital.toString());

        report.setAdjustedMarginalTaxRate(marginalTaxRate.toString());

        BigDecimal adjustedNopat = calculateAdjustedNopat(data.adjustedOperatingIncome, marginalTaxRate);
        report.setAdjustedNopat(adjustedNopat.toString());

        BigDecimal adjustedRoic = calculateAdjustedRoic(adjustedNopat, adjInvestedCapital);
        report.setAdjustedRoic(adjustedRoic.toString());

        BigDecimal reportedNopat = calculateAdjustedNopat(data.ebit0, marginalTaxRate);
        report.setReportedNopat(reportedNopat.toString());
        report.setReportedRoic(data.reportedRoic != null ? data.reportedRoic.toString() : "0");

        BigDecimal salesToCapital = calculateSalesToCapital(data.revenue0, adjInvestedCapital);
        report.setAdjustedSalesToCapital(salesToCapital.toString());

        report.setReportedSalesToCapital(data.reportedSalesToCapital != null ? data.reportedSalesToCapital.toString() : "0");

        data.adjInvestedCapital = adjInvestedCapital;
        data.marginalTaxRate = marginalTaxRate;
    }

    private void calculateEquityAndEarningsMetrics(FinancialAdjustmentReport report, RdAdjustmentData data) {
        BigDecimal adjustedNetIncome = calculateAdjustedNetIncome(data.netIncome0, data.adjustedOperatingIncome, data.ebit0);
        report.setAdjustedNetIncome(adjustedNetIncome.toString());
        report.setReportedNetIncome(data.netIncome0.toString());

        // Damodaran Fix: Adjusted Equity = Reported Equity + Research Asset - (Research Asset * Marginal Tax Rate)
        BigDecimal taxBenefit = data.researchAsset.multiply(data.marginalTaxRate);
        BigDecimal adjustedBookValueOfEquity = calculateAdjustedBookValueOfEquity(data.totalStockholdersEquity, data.researchAsset).subtract(taxBenefit);
        report.setAdjustedBookValueOfEquity(adjustedBookValueOfEquity.toString());
        report.setReportedBookValueOfEquity(data.totalStockholdersEquity.toString());

        data.adjustedEps = calculateAdjustedEps(adjustedNetIncome, data.weightedAverageShsOutDil);
        report.setAdjustedEps(data.adjustedEps.toString());

        report.setReportedEps(data.reportedEps0.toString());
    }

    private void calculateCashFlowAndEbitdaMetrics(FinancialAdjustmentReport report, RdAdjustmentData data) {
        BigDecimal adjustedFreeCashFlow = calculateAdjustedFreeCashFlow(data.operatingCashFlow, data.capitalExpenditure);
        report.setAdjustedFreeCashFlow(adjustedFreeCashFlow.toString());
        report.setReportedFreeCashFlow(data.reportedFreeCashFlow0.toString());

        data.adjustedEbitda = calculateAdjustedEbitda(data.adjustedOperatingIncome, data.depreciationAndAmortization0);
        report.setAdjustedEbitda(data.adjustedEbitda.toString());

        report.setReportedEbitda(data.reportedEbitda0.toString());
    }

    private void calculateValuationAndLeverageMetrics(FinancialAdjustmentReport report, RdAdjustmentData data) {
        BigDecimal netDebt = calculateNetDebt(data.totalDebt, data.cashAndCashEquivalents);
        BigDecimal netDebtToAdjustedEbitda = calculateNetDebtToAdjustedEbitda(netDebt, data.adjustedEbitda);
        report.setAdjustedNetDebtToEbitda(netDebtToAdjustedEbitda.toString());

        report.setReportedNetDebtToEbitda(data.reportedNetDebtToEbitda != null ? data.reportedNetDebtToEbitda.toString() : "0");

        BigDecimal adjustedEbitToInterest = calculateAdjustedEbitToInterest(data.adjustedOperatingIncome, data.interestExpense0);
        report.setAdjustedEbitToInterest(adjustedEbitToInterest.toString());

        report.setReportedEbitToInterest(data.reportedInterestCoverage != null ? data.reportedInterestCoverage.toString() : "0");

        BigDecimal adjustedPe = calculateAdjustedPe(data.currentSharePrice, data.adjustedEps);
        report.setAdjustedPe(adjustedPe.toString());

        report.setReportedPe(data.reportedPe != null ? data.reportedPe.toString() : "0");

        BigDecimal enterpriseValue = calculateEnterpriseValue(data.marketCap, data.totalDebt, data.cashAndCashEquivalents);
        BigDecimal evToAdjustedEbitda = calculateEvToAdjustedEbitda(enterpriseValue, data.adjustedEbitda);
        report.setAdjustedEvToEbitda(evToAdjustedEbitda.toString());

        report.setReportedEvToEbitda(data.reportedEvToEbitda != null ? data.reportedEvToEbitda.toString() : "0");

        // Set fields for future recalculations
        report.setWeightedAverageShsOutDil(data.weightedAverageShsOutDil != null ? data.weightedAverageShsOutDil.toString() : "0");
        report.setTotalDebt(data.totalDebt != null ? data.totalDebt.toString() : "0");
        report.setCashAndCashEquivalents(data.cashAndCashEquivalents != null ? data.cashAndCashEquivalents.toString() : "0");
    }

    private static class RdAdjustmentData {
        BigDecimal rd0, rd_1, rd_2, rd_3, rd_4;
        BigDecimal ebit0, reportedEbitda0, revenue0, incomeTaxExpense0, incomeBeforeTax0, interestExpense0, netIncome0, depreciationAndAmortization0, otherExpenses0;
        BigDecimal totalDebt, totalEquity, cashAndCashEquivalents, totalStockholdersEquity;
        BigDecimal stockBasedCompensation, operatingCashFlow, capitalExpenditure, reportedFreeCashFlow0;
        BigDecimal currentSharePrice, marketCap, weightedAverageShsOutDil, reportedEps0;
        BigDecimal reportedRoic, reportedPe, reportedNetDebtToEbitda, reportedSalesToCapital, reportedInterestCoverage, reportedEvToEbitda;
        BigDecimal researchAsset;
        BigDecimal adjustedOperatingIncome;
        BigDecimal adjInvestedCapital;
        BigDecimal marginalTaxRate;
        BigDecimal adjustedEps;
        BigDecimal adjustedEbitda;
    }

    private BigDecimal calculateResearchAsset(BigDecimal rd0, BigDecimal rd_1, BigDecimal rd_2, BigDecimal rd_3, BigDecimal rd_4) {
        return rd0.multiply(BigDecimal.valueOf(1.0))
                .add(rd_1.multiply(BigDecimal.valueOf(0.8)))
                .add(rd_2.multiply(BigDecimal.valueOf(0.6)))
                .add(rd_3.multiply(BigDecimal.valueOf(0.4)))
                .add(rd_4.multiply(BigDecimal.valueOf(0.2)));
    }

    private BigDecimal calculateRdCapitalizationAdjustment(BigDecimal currentRd, BigDecimal rdAmortization) {
        return currentRd.subtract(rdAmortization);
    }

    private BigDecimal calculateAdjustedOperatingIncome(BigDecimal ebit0, BigDecimal rdCapitalizationAdjustment) {
        return ebit0.add(rdCapitalizationAdjustment);
    }

    private BigDecimal calculateInvestedCapitalReported(BigDecimal totalDebt, BigDecimal totalEquity, BigDecimal cashAndCashEquivalents) {
        return totalDebt.add(totalEquity).subtract(cashAndCashEquivalents);
    }

    private BigDecimal calculateAdjInvestedCapital(BigDecimal investedCapitalReported, BigDecimal researchAsset) {
        return investedCapitalReported.add(researchAsset);
    }

    private BigDecimal calculateMarginalTaxRate(BigDecimal incomeTaxExpense0, BigDecimal incomeBeforeTax0) {
        BigDecimal marginalTaxRate = BigDecimal.ZERO;
        if (incomeBeforeTax0.compareTo(BigDecimal.ZERO) != 0) {
            marginalTaxRate = incomeTaxExpense0.divide(incomeBeforeTax0, 4, RoundingMode.HALF_UP);
        }
        
        if (marginalTaxRate.compareTo(BigDecimal.ZERO) < 0) {
            marginalTaxRate = BigDecimal.ZERO;
        } else if (marginalTaxRate.compareTo(BigDecimal.ONE) > 0) {
            marginalTaxRate = BigDecimal.ONE;
        }
        return marginalTaxRate;
    }

    private BigDecimal calculateAdjustedNopat(BigDecimal adjustedOperatingIncome, BigDecimal marginalTaxRate) {
        return adjustedOperatingIncome.multiply(BigDecimal.ONE.subtract(marginalTaxRate));
    }

    private BigDecimal calculateAdjustedRoic(BigDecimal adjustedNopat, BigDecimal adjInvestedCapital) {
        BigDecimal adjustedRoic = BigDecimal.ZERO;
        if (adjInvestedCapital.compareTo(BigDecimal.ZERO) != 0) {
            adjustedRoic = adjustedNopat.divide(adjInvestedCapital, 4, RoundingMode.HALF_UP);
        }
        return adjustedRoic;
    }

    private BigDecimal calculateSalesToCapital(BigDecimal revenue0, BigDecimal adjInvestedCapital) {
        BigDecimal salesToCapital = BigDecimal.ZERO;
        if (adjInvestedCapital.compareTo(BigDecimal.ZERO) != 0) {
            salesToCapital = revenue0.divide(adjInvestedCapital, 4, RoundingMode.HALF_UP);
        }
        return salesToCapital;
    }

    private BigDecimal calculateAdjustedNetIncome(BigDecimal reportedNetIncome, BigDecimal adjustedOperatingIncome, BigDecimal reportedOperatingIncome) {
        BigDecimal delta = adjustedOperatingIncome.subtract(reportedOperatingIncome);
        return reportedNetIncome.add(delta);
    }

    private BigDecimal calculateAdjustedBookValueOfEquity(BigDecimal totalStockholdersEquity, BigDecimal researchAsset) {
        return totalStockholdersEquity.add(researchAsset);
    }

    private BigDecimal calculateAdjustedEps(BigDecimal netIncome, BigDecimal weightedAverageShsOutDil) {
        BigDecimal adjustedEps = BigDecimal.ZERO;
        if (weightedAverageShsOutDil.compareTo(BigDecimal.ZERO) != 0) {
            adjustedEps = netIncome.divide(weightedAverageShsOutDil, 4, RoundingMode.HALF_UP);
        }
        return adjustedEps;
    }

    private BigDecimal calculateAdjustedFreeCashFlow(BigDecimal operatingCashFlow, BigDecimal capitalExpenditure) {
        return operatingCashFlow.subtract(capitalExpenditure);
    }

    private BigDecimal calculateAdjustedEbitda(BigDecimal adjustedOperatingIncome, BigDecimal depreciationAndAmortization0) {
        return adjustedOperatingIncome.add(depreciationAndAmortization0);
    }

    private BigDecimal calculateNetDebt(BigDecimal totalDebt, BigDecimal cashAndCashEquivalents) {
        return totalDebt.subtract(cashAndCashEquivalents);
    }

    private BigDecimal calculateNetDebtToAdjustedEbitda(BigDecimal netDebt, BigDecimal adjustedEbitda) {
        BigDecimal netDebtToAdjustedEbitda = BigDecimal.ZERO;
        if (adjustedEbitda.compareTo(BigDecimal.ZERO) != 0) {
            netDebtToAdjustedEbitda = netDebt.divide(adjustedEbitda, 4, RoundingMode.HALF_UP);
        }
        return netDebtToAdjustedEbitda;
    }

    private BigDecimal calculateAdjustedEbitToInterest(BigDecimal adjustedOperatingIncome, BigDecimal interestExpense0) {
        BigDecimal adjustedEbitToInterest = BigDecimal.ZERO;
        if (interestExpense0.compareTo(BigDecimal.ZERO) != 0) {
            adjustedEbitToInterest = adjustedOperatingIncome.divide(interestExpense0, 4, RoundingMode.HALF_UP);
        } else if (adjustedOperatingIncome.compareTo(BigDecimal.ZERO) > 0) {
            adjustedEbitToInterest = BigDecimal.valueOf(Double.MAX_VALUE);
        }
        return adjustedEbitToInterest;
    }

    private BigDecimal calculateAdjustedPe(BigDecimal currentSharePrice, BigDecimal adjustedEps) {
        BigDecimal adjustedPe = BigDecimal.ZERO;
        if (adjustedEps.compareTo(BigDecimal.ZERO) != 0) {
            adjustedPe = currentSharePrice.divide(adjustedEps, 4, RoundingMode.HALF_UP);
        }
        return adjustedPe;
    }

    private BigDecimal calculateEnterpriseValue(BigDecimal marketCap, BigDecimal totalDebt, BigDecimal cashAndCashEquivalents) {
        return marketCap.add(totalDebt).subtract(cashAndCashEquivalents);
    }

    private BigDecimal calculateEvToAdjustedEbitda(BigDecimal enterpriseValue, BigDecimal adjustedEbitda) {
        BigDecimal evToAdjustedEbitda = BigDecimal.ZERO;
        if (adjustedEbitda.compareTo(BigDecimal.ZERO) != 0) {
            evToAdjustedEbitda = enterpriseValue.divide(adjustedEbitda, 4, RoundingMode.HALF_UP);
        }
        return evToAdjustedEbitda;
    }

    private BigDecimal getLatestStockPrice(String ticker) {
        try {
            Optional<GlobalQuote> quoteOpt = quoteService.getLastStockQuote(ticker).blockOptional();
            if (quoteOpt.isPresent()) {
                GlobalQuote quote = quoteOpt.get();
                if (quote.getPrice() != null && !quote.getPrice().isEmpty()) {
                    return new BigDecimal(quote.getPrice());
                }
                if (quote.getAdjClose() != null && !quote.getAdjClose().isEmpty()) {
                    return new BigDecimal(quote.getAdjClose());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("    Error getting latest quote for {}: {}", ticker, e.getMessage());
        }
        return null;
    }
}
