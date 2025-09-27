package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.*;
import com.testehan.finana.util.SafeParser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ValuationService {

    private final CompanyOverviewRepository companyOverviewRepository;
    private final StockQuotesRepository stockQuotesRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final SharesOutstandingRepository sharesOutstandingRepository;

    private final FMPService fmpService;
    private final SafeParser safeParser;

    public ValuationService(CompanyOverviewRepository companyOverviewRepository,
                            StockQuotesRepository stockQuotesRepository,
                            IncomeStatementRepository incomeStatementRepository,
                            BalanceSheetRepository balanceSheetRepository,
                            CashFlowRepository cashFlowRepository, SharesOutstandingRepository sharesOutstandingRepository,
                            FMPService fmpService,
                            SafeParser safeParser) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.stockQuotesRepository = stockQuotesRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.sharesOutstandingRepository = sharesOutstandingRepository;
        this.fmpService = fmpService;
        this.safeParser = safeParser;
    }

    public DcfCalculationData getDcfCalculationData(String ticker) {
        // Fetch Company Meta
        DcfCalculationData.CompanyMeta meta = getCompanyMeta(ticker);

        // Fetch Company Overview for historical assumptions that need it
        Optional<CompanyOverview> companyOverviewOptional = companyOverviewRepository.findBySymbol(ticker);

        // Fetch Income Data (TTM)
        DcfCalculationData.IncomeData income = getTtmIncomeData(ticker);

        // Fetch Balance Sheet Data (MRQ)
        DcfCalculationData.BalanceSheetData balanceSheet = getMrqBalanceSheetData(ticker);

        // Fetch Cash Flow Data (TTM)
        DcfCalculationData.CashFlowData cashFlow = getTtmCashFlowData(ticker);

        // Fetch Historical Assumptions
        DcfCalculationData.HistoricalAssumptions assumptions = getHistoricalAssumptions(ticker, income, companyOverviewOptional);

        return DcfCalculationData.builder()
                .meta(meta)
                .income(income)
                .balanceSheet(balanceSheet)
                .cashFlow(cashFlow)
                .assumptions(assumptions)
                .build();
    }

    private DcfCalculationData.CompanyMeta getCompanyMeta(String ticker) {
        Optional<CompanyOverview> companyOverviewOptional = companyOverviewRepository.findBySymbol(ticker);
        Optional<SharesOutstandingData> sharesOutstandingDataOptional = sharesOutstandingRepository.findBySymbol(ticker);
        Optional<GlobalQuote> globalQuoteOptional = stockQuotesRepository.findBySymbol(ticker)
                .flatMap(quotes -> quotes.getQuotes().stream().findFirst());

        String companyName = companyOverviewOptional.map(CompanyOverview::getCompanyName).orElse("N/A");
        String currency = companyOverviewOptional.map(CompanyOverview::getCurrency).orElse("USD");
        BigDecimal currentSharePrice = globalQuoteOptional.map(gq -> safeParser.parse(gq.getAdjClose())).orElse(BigDecimal.ZERO);

        BigDecimal sharesOutstanding = null;
        if (sharesOutstandingDataOptional.isPresent() && !sharesOutstandingDataOptional.get().getData().isEmpty()) {

            SharesOutstandingReport latest = sharesOutstandingDataOptional.get()
                    .getData()
                    .stream()
                    .sorted(Comparator.comparing(SharesOutstandingReport::getDate).reversed())
                    .findFirst().get();

            sharesOutstanding = safeParser.parse(latest.getSharesOutstandingBasic());
        }

        return DcfCalculationData.CompanyMeta.builder()
                .ticker(ticker)
                .companyName(companyName)
                .currency(currency)
                .currentSharePrice(currentSharePrice)
                .sharesOutstanding(sharesOutstanding)
                .lastUpdated(LocalDate.now())
                .build();
    }

    private DcfCalculationData.IncomeData getTtmIncomeData(String ticker) {
        List<IncomeReport> quarterlyReports = incomeStatementRepository.findBySymbol(ticker)
                .map(IncomeStatementData::getQuarterlyReports)
                .orElse(List.of());

        // Sort by date descending and take the last 4 for TTM
        List<IncomeReport> ttmReports = quarterlyReports.stream()
                .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                .limit(4)
                .collect(Collectors.toList());

        if (ttmReports.size() < 4) {
            // Handle insufficient data, return default or throw exception
            return DcfCalculationData.IncomeData.builder()
                    .revenue(BigDecimal.ZERO)
                    .ebit(BigDecimal.ZERO)
                    .interestExpense(BigDecimal.ZERO)
                    .incomeTaxExpense(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal revenue = ttmReports.stream()
                .map(IncomeReport::getRevenue)
                .map(safeParser::parse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ebit = ttmReports.stream()
                .map(IncomeReport::getEbit)
                .map(safeParser::parse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interestExpense = ttmReports.stream()
                .map(IncomeReport::getInterestExpense)
                .map(safeParser::parse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal incomeTaxExpense = ttmReports.stream()
                .map(IncomeReport::getIncomeTaxExpense)
                .map(safeParser::parse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DcfCalculationData.IncomeData.builder()
                .revenue(revenue)
                .ebit(ebit)
                .interestExpense(interestExpense)
                .incomeTaxExpense(incomeTaxExpense)
                .build();
    }

    private DcfCalculationData.BalanceSheetData getMrqBalanceSheetData(String ticker) {
        List<BalanceSheetReport> quarterlyReports = balanceSheetRepository.findBySymbol(ticker)
                .map(BalanceSheetData::getQuarterlyReports)
                .orElse(List.of());

        BalanceSheetReport mrqReport = quarterlyReports.stream()
                .sorted(Comparator.comparing(BalanceSheetReport::getDate).reversed())
                .findFirst()
                .orElse(null);

        if (mrqReport == null) {
            return DcfCalculationData.BalanceSheetData.builder()
                    .totalCashAndEquivalents(BigDecimal.ZERO)
                    .totalShortTermDebt(BigDecimal.ZERO)
                    .totalLongTermDebt(BigDecimal.ZERO)
                    .totalCurrentAssets(BigDecimal.ZERO)
                    .totalCurrentLiabilities(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal totalCashAndEquivalents = safeParser.parse(mrqReport.getCashAndCashEquivalents());
        BigDecimal totalShortTermDebt = safeParser.parse(mrqReport.getShortTermDebt());
        BigDecimal totalLongTermDebt = safeParser.parse(mrqReport.getLongTermDebt());
        BigDecimal totalCurrentAssets = safeParser.parse(mrqReport.getTotalCurrentAssets());
        BigDecimal totalCurrentLiabilities = safeParser.parse(mrqReport.getTotalCurrentLiabilities());

        return DcfCalculationData.BalanceSheetData.builder()
                .totalCashAndEquivalents(totalCashAndEquivalents)
                .totalShortTermDebt(totalShortTermDebt)
                .totalLongTermDebt(totalLongTermDebt)
                .totalCurrentAssets(totalCurrentAssets)
                .totalCurrentLiabilities(totalCurrentLiabilities)
                .build();
    }

    private DcfCalculationData.CashFlowData getTtmCashFlowData(String ticker) {
        List<CashFlowReport> quarterlyReports = cashFlowRepository.findBySymbol(ticker)
                .map(CashFlowData::getQuarterlyReports)
                .orElse(List.of());

        List<CashFlowReport> ttmReports = quarterlyReports.stream()
                .sorted(Comparator.comparing(CashFlowReport::getDate).reversed())
                .limit(4)
                .collect(Collectors.toList());

        if (ttmReports.size() < 4) {
            return DcfCalculationData.CashFlowData.builder()
                    .depreciationAndAmortization(BigDecimal.ZERO)
                    .capitalExpenditure(BigDecimal.ZERO)
                    .stockBasedCompensation(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal depreciationAndAmortization = ttmReports.stream()
                .map(CashFlowReport::getDepreciationAndAmortization)
                .map(safeParser::parse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal capitalExpenditure = ttmReports.stream()
                .map(CashFlowReport::getCapitalExpenditure)
                .map(safeParser::parse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal stockBasedCompensation = ttmReports.stream()
                .map(CashFlowReport::getStockBasedCompensation)
                .map(safeParser::parse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DcfCalculationData.CashFlowData.builder()
                .depreciationAndAmortization(depreciationAndAmortization)
                .capitalExpenditure(capitalExpenditure)
                .stockBasedCompensation(stockBasedCompensation)
                .build();
    }

    private DcfCalculationData.HistoricalAssumptions getHistoricalAssumptions(String ticker, DcfCalculationData.IncomeData incomeData, Optional<CompanyOverview> companyOverviewOptional) {
        double beta = companyOverviewOptional.map(co -> safeParser.parse(co.getBeta()).doubleValue()).orElse(1.0); // Default beta to 1.0

        // Hardcoding for now, these would typically come from a configuration or external service
        double riskFreeRate = 0.042; // Example: 4.2% - 10Y US Treasury yield
        double marketRiskPremium = 0.055; // Example: 5.5%

        // Effective Tax Rate: (Income Tax Expense / Pre-Tax Income)
        // Need to fetch more income reports to calculate Pre-Tax Income
        // For simplicity, let's assume pre-tax income is EBIT for now and refine later.
        double effectiveTaxRate = incomeData.ebit().compareTo(BigDecimal.ZERO) != 0
                ? incomeData.incomeTaxExpense().divide(incomeData.ebit(), 4, BigDecimal.ROUND_HALF_UP).doubleValue()
                : 0.21; // Default to US corporate tax rate if EBIT is zero

        // Revenue Growth CAGR (3-year) and Average EBIT Margin (3-year)
        // This requires fetching annual income statements for the last 3 years
        List<IncomeReport> annualReports = incomeStatementRepository.findBySymbol(ticker)
                .map(IncomeStatementData::getAnnualReports)
                .orElse(List.of());

        List<IncomeReport> lastThreeAnnualReports = annualReports.stream()
                .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                .limit(3)
                .collect(Collectors.toList());

        double revenueGrowthCagr3Year = 0.0;
        double averageEbitMargin3Year = 0.0;

        if (lastThreeAnnualReports.size() >= 3) {
            // Calculate 3-year Revenue Growth CAGR
            BigDecimal revenueYear0 = safeParser.parse(lastThreeAnnualReports.get(0).getRevenue());
            BigDecimal revenueYear3 = safeParser.parse(lastThreeAnnualReports.get(2).getRevenue());

            if (revenueYear3.compareTo(BigDecimal.ZERO) != 0) {
                revenueGrowthCagr3Year = Math.pow(revenueYear0.divide(revenueYear3, 4, BigDecimal.ROUND_HALF_UP).doubleValue(), 1.0/3.0) - 1.0;
            }

            // Calculate 3-year Average EBIT Margin
            averageEbitMargin3Year = lastThreeAnnualReports.stream()
                    .mapToDouble(report -> {
                        BigDecimal totalRevenue = safeParser.parse(report.getRevenue());
                        BigDecimal ebitda = safeParser.parse(report.getEbitda());
                        return totalRevenue.compareTo(BigDecimal.ZERO) != 0
                                ? ebitda.divide(totalRevenue, 4, BigDecimal.ROUND_HALF_UP).doubleValue()
                                : 0.0;
                    })
                    .average()
                    .orElse(0.0);
        }

        return DcfCalculationData.HistoricalAssumptions.builder()
                .beta(beta)
                .riskFreeRate(riskFreeRate)
                .marketRiskPremium(marketRiskPremium)
                .effectiveTaxRate(effectiveTaxRate)
                .revenueGrowthCagr3Year(revenueGrowthCagr3Year)
                .averageEbitMargin3Year(averageEbitMargin3Year)
                .build();
    }
}