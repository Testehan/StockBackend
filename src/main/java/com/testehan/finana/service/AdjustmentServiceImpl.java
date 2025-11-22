package com.testehan.finana.service;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.adjustment.FinancialAdjustment;
import com.testehan.finana.model.adjustment.FinancialAdjustmentReport;
import com.testehan.finana.model.finstatement.*;
import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.CashFlowRepository;
import com.testehan.finana.repository.FinancialAdjustmentRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.util.SafeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
    private final SafeParser safeParser;

    public AdjustmentServiceImpl(IncomeStatementRepository incomeStatementRepository, FinancialAdjustmentRepository financialAdjustmentRepository, BalanceSheetRepository balanceSheetRepository, CashFlowRepository cashFlowRepository, CompanyDataService companyDataService, QuoteService quoteService, SafeParser safeParser) {
        this.incomeStatementRepository = incomeStatementRepository;
        this.financialAdjustmentRepository = financialAdjustmentRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.companyDataService = companyDataService;
        this.quoteService = quoteService;
        this.safeParser = safeParser;
    }

    @Override
    public FinancialAdjustment getFinancialAdjustments(String symbol) {
        BigDecimal price = getLatestStockPrice(symbol);

        Optional<FinancialAdjustment> existingAdjustment = financialAdjustmentRepository.findBySymbol(symbol);
        if (existingAdjustment.isPresent()) {
            FinancialAdjustment adjustment = existingAdjustment.get();
            if (price != null) {
                updatePriceDependentMetrics(adjustment, price);
            }
            return adjustment;
        }

        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(symbol);
        Optional<BalanceSheetData> balanceSheetDataOptional = balanceSheetRepository.findBySymbol(symbol);
        Optional<CashFlowData> cashFlowDataOptional = cashFlowRepository.findBySymbol(symbol);
        Optional<com.testehan.finana.model.CompanyOverview> companyOverviewOptional = companyDataService.getCompanyOverview(symbol).blockOptional()
            .flatMap(list -> list.stream().findFirst());

        if (incomeStatementDataOptional.isEmpty() || incomeStatementDataOptional.get().getAnnualReports() == null ||
                balanceSheetDataOptional.isEmpty() || balanceSheetDataOptional.get().getAnnualReports() == null ||
                cashFlowDataOptional.isEmpty() || cashFlowDataOptional.get().getAnnualReports() == null ||
                companyOverviewOptional.isEmpty() || price == null) {
            return new FinancialAdjustment();
        }

        List<IncomeReport> annualIncomeReports = incomeStatementDataOptional.get().getAnnualReports();
        List<BalanceSheetReport> annualBalanceSheetReports = balanceSheetDataOptional.get().getAnnualReports();
        List<CashFlowReport> annualCashFlowReports = cashFlowDataOptional.get().getAnnualReports();

        if (annualIncomeReports.size() < 5 || annualBalanceSheetReports.size() < 1 || annualCashFlowReports.size() < 1) { 
            return new FinancialAdjustment();
        }

        // Sort income reports by date (assuming YYYY-MM-DD format) to get the latest 5 years
        List<IncomeReport> last5YearsOfIncomeReports = annualIncomeReports.stream()
                .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                .limit(5)
                .collect(Collectors.toList());

        // Get the latest balance sheet report matching the latest income report year
        String latestIncomeReportYear = last5YearsOfIncomeReports.get(0).getDate().substring(0, 4);
        Optional<BalanceSheetReport> latestBalanceSheetReport = annualBalanceSheetReports.stream()
                .filter(report -> report.getDate().startsWith(latestIncomeReportYear))
                .sorted(Comparator.comparing(BalanceSheetReport::getDate).reversed())
                .findFirst();

        // Get the latest cash flow report matching the latest income report year
        Optional<CashFlowReport> latestCashFlowReport = annualCashFlowReports.stream()
                .filter(report -> report.getDate().startsWith(latestIncomeReportYear))
                .sorted(Comparator.comparing(CashFlowReport::getDate).reversed())
                .findFirst();


        if (latestBalanceSheetReport.isEmpty() || latestCashFlowReport.isEmpty()) {
            return new FinancialAdjustment();
        }

        FinancialAdjustmentReport adjustmentReport = calculateRdAdjustment(last5YearsOfIncomeReports, latestBalanceSheetReport.get(), latestCashFlowReport.get(), companyOverviewOptional.get(), price);

        FinancialAdjustment financialAdjustment = new FinancialAdjustment();
        financialAdjustment.setSymbol(symbol);
        financialAdjustment.setLastUpdated(LocalDateTime.now());
        financialAdjustment.setAnnualAdjustments(List.of(adjustmentReport));

        return financialAdjustmentRepository.save(financialAdjustment);
    }

    private void updatePriceDependentMetrics(FinancialAdjustment adjustment, BigDecimal price) {
        if (adjustment.getAnnualAdjustments() != null) {
            for (FinancialAdjustmentReport report : adjustment.getAnnualAdjustments()) {
                BigDecimal adjustedEps = safeParser.parse(report.getAdjustedEps());
                BigDecimal newPe = calculateAdjustedPe(price, adjustedEps);
                report.setAdjustedPe(newPe.toString());
            }
        }
    }

    @Override
    public void deleteFinancialAdjustmentBySymbol(String symbol) {
        financialAdjustmentRepository.findBySymbol(symbol).ifPresent(financialAdjustmentRepository::delete);
    }

    private FinancialAdjustmentReport calculateRdAdjustment(List<IncomeReport> incomeReports, BalanceSheetReport balanceSheetReport, CashFlowReport cashFlowReport, com.testehan.finana.model.CompanyOverview companyOverview, BigDecimal price) {
        FinancialAdjustmentReport report = new FinancialAdjustmentReport();

        // Ensure we have exactly 5 years, sorted in descending order (latest first)
        if (incomeReports.size() < 5) {
            return report;
        }

        IncomeReport year0Income = incomeReports.get(0);
        RdAdjustmentData data = parseFinancialData(incomeReports, balanceSheetReport, cashFlowReport, companyOverview, price);

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
        calculateValuationAndLeverageMetrics(report, data);

        return report;
    }

    private RdAdjustmentData parseFinancialData(List<IncomeReport> incomeReports, BalanceSheetReport balanceSheetReport, CashFlowReport cashFlowReport, CompanyOverview companyOverview, BigDecimal price) {
        RdAdjustmentData data = new RdAdjustmentData();
        IncomeReport year0Income = incomeReports.get(0);

        data.rd0 = safeParser.parse(year0Income.getResearchAndDevelopmentExpenses());
        data.rd_1 = safeParser.parse(incomeReports.get(1).getResearchAndDevelopmentExpenses());
        data.rd_2 = safeParser.parse(incomeReports.get(2).getResearchAndDevelopmentExpenses());
        data.rd_3 = safeParser.parse(incomeReports.get(3).getResearchAndDevelopmentExpenses());
        data.rd_4 = safeParser.parse(incomeReports.get(4).getResearchAndDevelopmentExpenses());

        data.ebit0 = safeParser.parse(year0Income.getOperatingIncome());
        data.revenue0 = safeParser.parse(year0Income.getRevenue());
        data.incomeTaxExpense0 = safeParser.parse(year0Income.getIncomeTaxExpense());
        data.incomeBeforeTax0 = safeParser.parse(year0Income.getIncomeBeforeTax());
        data.interestExpense0 = safeParser.parse(year0Income.getInterestExpense());
        data.netIncome0 = safeParser.parse(year0Income.getNetIncome());
        data.depreciationAndAmortization0 = safeParser.parse(year0Income.getDepreciationAndAmortization());

        data.totalDebt = safeParser.parse(balanceSheetReport.getTotalDebt());
        data.totalEquity = safeParser.parse(balanceSheetReport.getTotalEquity());
        data.cashAndCashEquivalents = safeParser.parse(balanceSheetReport.getCashAndCashEquivalents());
        data.totalStockholdersEquity = safeParser.parse(balanceSheetReport.getTotalStockholdersEquity());

        data.stockBasedCompensation = safeParser.parse(cashFlowReport.getStockBasedCompensation());
        data.operatingCashFlow = safeParser.parse(cashFlowReport.getOperatingCashFlow());
        data.capitalExpenditure = safeParser.parse(cashFlowReport.getCapitalExpenditure());

        data.currentSharePrice = price;
        data.marketCap = safeParser.parse(companyOverview.getMarketCap());
        data.weightedAverageShsOutDil = safeParser.parse(year0Income.getWeightedAverageShsOutDil());

        return data;
    }

    private void calculateOperatingAndProfitabilityMetrics(FinancialAdjustmentReport report, RdAdjustmentData data) {
        BigDecimal rdAmortization = data.rd_4;
        BigDecimal rdCapitalizationAdjustment = calculateRdCapitalizationAdjustment(data.rd0, rdAmortization);
        data.adjustedOperatingIncome = calculateAdjustedOperatingIncome(data.ebit0, rdCapitalizationAdjustment);

        report.setRdCapitalizationAdjustment(rdCapitalizationAdjustment.toString());
        report.setAdjustedOperatingIncome(data.adjustedOperatingIncome.toString());

        BigDecimal investedCapitalReported = calculateInvestedCapitalReported(data.totalDebt, data.totalEquity, data.cashAndCashEquivalents);
        BigDecimal adjInvestedCapital = calculateAdjInvestedCapital(investedCapitalReported, data.researchAsset);

        BigDecimal marginalTaxRate = calculateMarginalTaxRate(data.incomeTaxExpense0, data.incomeBeforeTax0);
        report.setAdjustedMarginalTaxRate(marginalTaxRate.toString());

        BigDecimal adjustedNopat = calculateAdjustedNopat(data.adjustedOperatingIncome, marginalTaxRate);
        report.setAdjustedNopat(adjustedNopat.toString());

        BigDecimal adjustedRoic = calculateAdjustedRoic(adjustedNopat, adjInvestedCapital);
        report.setAdjustedRoic(adjustedRoic.toString());

        BigDecimal salesToCapital = calculateSalesToCapital(data.revenue0, adjInvestedCapital);
        report.setSalesToCapital(salesToCapital.toString());

        data.adjInvestedCapital = adjInvestedCapital;
        data.marginalTaxRate = marginalTaxRate;
    }

    private void calculateEquityAndEarningsMetrics(FinancialAdjustmentReport report, RdAdjustmentData data) {
        BigDecimal adjustedNetIncome = calculateAdjustedNetIncome(data.adjustedOperatingIncome, data.interestExpense0, data.incomeTaxExpense0);
        report.setAdjustedNetIncome(adjustedNetIncome.toString());

        BigDecimal dilutedNetIncome = calculateDilutedNetIncome(data.netIncome0, data.stockBasedCompensation);
        report.setDilutedNetIncome(dilutedNetIncome.toString());

        BigDecimal adjustedBookValueOfEquity = calculateAdjustedBookValueOfEquity(data.totalStockholdersEquity, data.researchAsset);
        report.setAdjustedBookValueOfEquity(adjustedBookValueOfEquity.toString());

        data.adjustedEps = calculateAdjustedEps(dilutedNetIncome, data.weightedAverageShsOutDil);
        report.setAdjustedEps(data.adjustedEps.toString());
    }

    private void calculateCashFlowAndEbitdaMetrics(FinancialAdjustmentReport report, RdAdjustmentData data) {
        BigDecimal adjustedFreeCashFlow = calculateAdjustedFreeCashFlow(data.operatingCashFlow, data.capitalExpenditure);
        report.setAdjustedFreeCashFlow(adjustedFreeCashFlow.toString());

        data.adjustedEbitda = calculateAdjustedEbitda(data.adjustedOperatingIncome, data.depreciationAndAmortization0);
        report.setAdjustedEbitda(data.adjustedEbitda.toString());
    }

    private void calculateValuationAndLeverageMetrics(FinancialAdjustmentReport report, RdAdjustmentData data) {
        BigDecimal netDebt = calculateNetDebt(data.totalDebt, data.cashAndCashEquivalents);
        BigDecimal netDebtToAdjustedEbitda = calculateNetDebtToAdjustedEbitda(netDebt, data.adjustedEbitda);
        report.setNetDebtToAdjustedEbitda(netDebtToAdjustedEbitda.toString());

        BigDecimal adjustedEbitToInterest = calculateAdjustedEbitToInterest(data.adjustedOperatingIncome, data.interestExpense0);
        report.setAdjustedEbitToInterest(adjustedEbitToInterest.toString());

        BigDecimal adjustedPe = calculateAdjustedPe(data.currentSharePrice, data.adjustedEps);
        report.setAdjustedPe(adjustedPe.toString());

        BigDecimal enterpriseValue = calculateEnterpriseValue(data.marketCap, data.totalDebt, data.cashAndCashEquivalents);
        BigDecimal evToAdjustedEbitda = calculateEvToAdjustedEbitda(enterpriseValue, data.adjustedEbitda);
        report.setEvToAdjustedEbitda(evToAdjustedEbitda.toString());
    }

    private static class RdAdjustmentData {
        BigDecimal rd0, rd_1, rd_2, rd_3, rd_4;
        BigDecimal ebit0, revenue0, incomeTaxExpense0, incomeBeforeTax0, interestExpense0, netIncome0, depreciationAndAmortization0;
        BigDecimal totalDebt, totalEquity, cashAndCashEquivalents, totalStockholdersEquity;
        BigDecimal stockBasedCompensation, operatingCashFlow, capitalExpenditure;
        BigDecimal currentSharePrice, marketCap, weightedAverageShsOutDil;
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

    private BigDecimal calculateAdjustedNetIncome(BigDecimal adjustedOperatingIncome, BigDecimal interestExpense0, BigDecimal incomeTaxExpense0) {
        return adjustedOperatingIncome
                .subtract(interestExpense0)
                .subtract(incomeTaxExpense0);
    }

    private BigDecimal calculateDilutedNetIncome(BigDecimal netIncome0, BigDecimal stockBasedCompensation) {
        return netIncome0.subtract(stockBasedCompensation);
    }

    private BigDecimal calculateAdjustedBookValueOfEquity(BigDecimal totalStockholdersEquity, BigDecimal researchAsset) {
        return totalStockholdersEquity.add(researchAsset);
    }

    private BigDecimal calculateAdjustedEps(BigDecimal dilutedNetIncome, BigDecimal weightedAverageShsOutDil) {
        BigDecimal adjustedEps = BigDecimal.ZERO;
        if (weightedAverageShsOutDil.compareTo(BigDecimal.ZERO) != 0) {
            adjustedEps = dilutedNetIncome.divide(weightedAverageShsOutDil, 4, RoundingMode.HALF_UP);
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
