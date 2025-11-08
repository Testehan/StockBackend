package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.DcfValuation;
import com.testehan.finana.model.valuation.dcf.ReverseDcfValuation;
import com.testehan.finana.model.valuation.Valuations;
import com.testehan.finana.model.valuation.growth.*;
import com.testehan.finana.repository.*;
import com.testehan.finana.util.SafeParser;
import com.testehan.finana.service.valuation.growth.GrowthValuationCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final ValuationsRepository valuationsRepository;

    private final FMPService fmpService;
    private final SafeParser safeParser;
    private final GrowthValuationCalculator growthValuationCalculator;

    public ValuationService(CompanyOverviewRepository companyOverviewRepository,
                            StockQuotesRepository stockQuotesRepository,
                            IncomeStatementRepository incomeStatementRepository,
                            BalanceSheetRepository balanceSheetRepository,
                            CashFlowRepository cashFlowRepository,
                            ValuationsRepository valuationsRepository, FMPService fmpService,
                            SafeParser safeParser,
                            GrowthValuationCalculator growthValuationCalculator) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.stockQuotesRepository = stockQuotesRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.valuationsRepository = valuationsRepository;
        this.fmpService = fmpService;
        this.safeParser = safeParser;
        this.growthValuationCalculator = growthValuationCalculator;
    }

    public void saveDcfValuation(DcfValuation dcfValuation) {
        dcfValuation.setValuationDate(LocalDateTime.now().toString());
        String ticker = dcfValuation.getDcfCalculationData().meta().ticker();
        Valuations valuations = valuationsRepository.findById(ticker).orElse(new Valuations());
        valuations.setTicker(ticker);
        valuations.getDcfValuations().add(dcfValuation);
        valuationsRepository.save(valuations);
    }

    public void saveReverseDcfValuation(ReverseDcfValuation reverseDcfValuation) {
        reverseDcfValuation.setValuationDate(LocalDateTime.now().toString());
        String ticker = reverseDcfValuation.getDcfCalculationData().meta().ticker();
        Valuations valuations = valuationsRepository.findById(ticker).orElse(new Valuations());
        valuations.setTicker(ticker);
        valuations.getReverseDcfValuations().add(reverseDcfValuation);
        valuationsRepository.save(valuations);
    }

    public List<DcfValuation> getDcfHistory(String ticker) {
        return valuationsRepository.findById(ticker)
                .map(Valuations::getDcfValuations)
                .orElse(java.util.Collections.emptyList());
    }

    public List<ReverseDcfValuation> getReverseDcfHistory(String ticker) {
        return valuationsRepository.findById(ticker)
                .map(Valuations::getReverseDcfValuations)
                .orElse(java.util.Collections.emptyList());
    }

    public GrowthValuation getGrowthCompanyValuationData(String ticker) {
        GrowthValuation growthValuation = new GrowthValuation();
        GrowthValuationData growthValuationData = initializeGrowthValuationData(ticker);
        GrowthUserInput growthUserInput = initializeGrowthUserInput(ticker, growthValuationData);
        
        growthValuation.setGrowthValuationData(growthValuationData);
        growthValuation.setGrowthUserInput(growthUserInput);
        return growthValuation;
    }

    private GrowthValuationData initializeGrowthValuationData(String ticker) {
        GrowthValuationData growthValuationData = new GrowthValuationData();
        ticker = ticker.toUpperCase();
        growthValuationData.setTicker(ticker);

        // Fetch Company Profile
        Optional<CompanyOverview> companyOverviewOptional = companyOverviewRepository.findBySymbol(ticker);
        companyOverviewOptional.ifPresent(overview -> {
            growthValuationData.setName(overview.getCompanyName());
            growthValuationData.setSector(overview.getSector());
            growthValuationData.setIndustry(overview.getIndustry());
            growthValuationData.setCurrency(overview.getCurrency());
        });

        // Fetch Market Data
        Optional<GlobalQuote> globalQuoteOptional = stockQuotesRepository.findBySymbol(ticker)
                .flatMap(quotes -> quotes.getQuotes().stream().findFirst());
        globalQuoteOptional.ifPresent(quote -> {
            growthValuationData.setCurrentSharePrice(safeParser.parse(quote.getAdjClose()).doubleValue());
            // marketCapitalization would need shares outstanding, which we'll get from income statement
        });
        // Risk-free rate can be hardcoded for now, or fetched from a config
        growthValuationData.setRiskFreeRate(0.042); // Example: 4.2% - 10Y US Treasury yield

        // Fetch Financial Statements (Annual Reports for multi-year history)
        List<IncomeReport> annualIncomeReports = incomeStatementRepository.findBySymbol(ticker)
                .map(IncomeStatementData::getAnnualReports)
                .orElse(java.util.Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(IncomeReport::getDate))
                .collect(Collectors.toList());

        List<BalanceSheetReport> annualBalanceSheetReports = balanceSheetRepository.findBySymbol(ticker)
                .map(BalanceSheetData::getAnnualReports)
                .orElse(java.util.Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(BalanceSheetReport::getDate))
                .collect(Collectors.toList());

        List<CashFlowReport> annualCashFlowReports = cashFlowRepository.findBySymbol(ticker)
                .map(CashFlowData::getAnnualReports)
                .orElse(java.util.Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(CashFlowReport::getDate))
                .collect(Collectors.toList());

        // Process Income Statements
        List<IncomeStatementYear> incomeStatementYears = new ArrayList<>();
        for (IncomeReport report : annualIncomeReports) {
            IncomeStatementYear year = new IncomeStatementYear();
            year.setFiscalYear(LocalDate.parse(report.getDate()).getYear());
            year.setRevenue(safeParser.parse(report.getRevenue()).doubleValue());
            year.setOperatingIncome(safeParser.parse(report.getOperatingIncome()).doubleValue());
            year.setPretaxIncome(safeParser.parse(report.getIncomeBeforeTax()).doubleValue());
            year.setNetIncome(safeParser.parse(report.getNetIncome()).doubleValue());
            incomeStatementYears.add(year);
        }
        growthValuationData.setIncomeStatements(incomeStatementYears);

        // Process Balance Sheets
        List<BalanceSheetYear> balanceSheetYears = new ArrayList<>();
        for (BalanceSheetReport report : annualBalanceSheetReports) {
            BalanceSheetYear year = new BalanceSheetYear();
            year.setFiscalYear(LocalDate.parse(report.getDate()).getYear());
            year.setCashAndEquivalents(safeParser.parse(report.getCashAndCashEquivalents()).doubleValue());
            year.setShortTermDebt(safeParser.parse(report.getShortTermDebt()).doubleValue());
            year.setLongTermDebt(safeParser.parse(report.getLongTermDebt()).doubleValue());
            year.setTotalAssets(safeParser.parse(report.getTotalAssets()).doubleValue());
            year.setTotalEquity(safeParser.parse(report.getTotalEquity()).doubleValue());
            balanceSheetYears.add(year);
        }
        growthValuationData.setBalanceSheets(balanceSheetYears);

        // Process Cash Flows
        List<CashFlowYear> cashFlowYears = new ArrayList<>();
        for (CashFlowReport report : annualCashFlowReports) {
            CashFlowYear year = new CashFlowYear();
            year.setFiscalYear(LocalDate.parse(report.getDate()).getYear());
            year.setOperatingCashFlow(safeParser.parse(report.getOperatingCashFlow()).doubleValue());
            year.setCapitalExpenditures(safeParser.parse(report.getCapitalExpenditure()).doubleValue());
            year.setDepreciationAndAmortization(safeParser.parse(report.getDepreciationAndAmortization()).doubleValue());
            year.setChangeInWorkingCapital(safeParser.parse(report.getChangeInWorkingCapital()).doubleValue());
            cashFlowYears.add(year);
        }
        growthValuationData.setCashFlows(cashFlowYears);

        // Tax Attributes (Placeholder - actual data source needed)
        growthValuationData.setNetOperatingLossCarryforward(0.0);
        growthValuationData.setNolExpirationYears(0);

        // Capital Structure and Share Counts (from latest reports)
        annualBalanceSheetReports.stream().max(Comparator.comparing(BalanceSheetReport::getDate)).ifPresent(latestReport -> {
            double latestTotalDebt = safeParser.parse(latestReport.getShortTermDebt()).doubleValue() 
                    + safeParser.parse(latestReport.getLongTermDebt()).doubleValue();
            growthValuationData.setTotalDebt(latestTotalDebt);
            growthValuationData.setCashBalance(safeParser.parse(latestReport.getCashAndCashEquivalents()).doubleValue());

            // Calculate Average Interest Rate
            if (annualBalanceSheetReports.size() >= 2 && annualIncomeReports.size() >= 1) {
                int latestReportIndex = annualBalanceSheetReports.indexOf(latestReport);
                BalanceSheetReport previousReport = null;
                if (latestReportIndex > 0) {
                    previousReport = annualBalanceSheetReports.get(latestReportIndex - 1);
                }

                IncomeReport latestIncomeReport = annualIncomeReports.stream()
                        .max(Comparator.comparing(IncomeReport::getDate))
                        .orElse(null);

                if (previousReport != null && latestIncomeReport != null) {
                    double previousTotalDebt = safeParser.parse(previousReport.getShortTermDebt()).doubleValue() 
                            + safeParser.parse(previousReport.getLongTermDebt()).doubleValue();
                    double averageDebt = (latestTotalDebt + previousTotalDebt) / 2.0;
                    double interestExpense = Math.abs(safeParser.parse(latestIncomeReport.getInterestExpense()).doubleValue());

                    if (averageDebt != 0) {
                        growthValuationData.setAverageInterestRate(interestExpense / averageDebt);
                    }
                }
            }
        });

        // Share Counts
        annualIncomeReports.stream().max(Comparator.comparing(IncomeReport::getDate)).ifPresent(latestReport -> {
            growthValuationData.setCommonSharesOutstanding(safeParser.parse(latestReport.getWeightedAverageShsOut()).doubleValue());
        });

        // Calculate Market Capitalization
        if (growthValuationData.getCommonSharesOutstanding() != 0 && growthValuationData.getCurrentSharePrice() != 0) {
            growthValuationData.setMarketCapitalization(
                growthValuationData.getCommonSharesOutstanding() * growthValuationData.getCurrentSharePrice()
            );
        }

        return growthValuationData;
    }

    private GrowthUserInput initializeGrowthUserInput(String ticker, GrowthValuationData growthValuationData) {
        GrowthUserInput growthUserInput = new GrowthUserInput();

        // Fetch annual income reports sorted by date descending for calculations
        List<IncomeReport> annualIncomeReports = incomeStatementRepository.findBySymbol(ticker)
                .map(IncomeStatementData::getAnnualReports)
                .orElse(java.util.Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                .collect(Collectors.toList());

        // Calculate marginalTaxRate from latest income statement if available
        if (!annualIncomeReports.isEmpty()) {
            IncomeReport latestReport = annualIncomeReports.get(0);
            double pretaxIncome = safeParser.parse(latestReport.getIncomeBeforeTax()).doubleValue();
            double incomeTaxExpense = safeParser.parse(latestReport.getIncomeTaxExpense()).doubleValue();
            if (pretaxIncome != 0) {
                growthUserInput.setMarginalTaxRate(incomeTaxExpense / pretaxIncome);
            } else {
                growthUserInput.setMarginalTaxRate(0.21); // Default to US corporate tax rate
            }
        }

        // Calculate initialRevenueGrowthRate as 3-year CAGR if data is available
        if (annualIncomeReports.size() >= 3) {
            BigDecimal revenueYear0 = safeParser.parse(annualIncomeReports.get(0).getRevenue());
            BigDecimal revenueYear3 = safeParser.parse(annualIncomeReports.get(2).getRevenue());

            if (revenueYear3.compareTo(BigDecimal.ZERO) != 0) {
                double revenueGrowthCagr3Year = Math.pow(
                    revenueYear0.divide(revenueYear3, 4, RoundingMode.HALF_UP).doubleValue(),
                    1.0 / 3.0
                ) - 1.0;
                growthUserInput.setInitialRevenueGrowthRate(revenueGrowthCagr3Year * 100); // Convert to percentage
            } else {
                growthUserInput.setInitialRevenueGrowthRate(0.0); // Default if year 3 revenue is zero
            }
        } else {
            growthUserInput.setInitialRevenueGrowthRate(0.0); // Default if insufficient data
        }

        // Calculate targetOperatingMargin as 3-year average operating margin if data is available
        if (annualIncomeReports.size() >= 3) {
            double averageOperatingMargin = annualIncomeReports.stream()
                    .limit(3)
                    .mapToDouble(report -> {
                        BigDecimal revenue = safeParser.parse(report.getRevenue());
                        BigDecimal operatingIncome = safeParser.parse(report.getOperatingIncome());
                        return revenue.compareTo(BigDecimal.ZERO) != 0
                                ? operatingIncome.divide(revenue, 4, RoundingMode.HALF_UP).doubleValue()
                                : 0.0;
                    })
                    .average()
                    .orElse(0.0);
            growthUserInput.setTargetOperatingMargin(averageOperatingMargin * 100); // Convert to percentage
        } else {
            growthUserInput.setTargetOperatingMargin(0.0); // Default if insufficient data
        }

        // Calculate reinvestmentAsPctOfRevenue as 3-year average if data is available
        // Net Reinvestment = Max(0, (|CapEx| - Depreciation) + ChangeInWorkingCapital)
        List<CashFlowReport> annualCashFlowReports = cashFlowRepository.findBySymbol(ticker)
                .map(CashFlowData::getAnnualReports)
                .orElse(java.util.Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(CashFlowReport::getDate).reversed())
                .collect(Collectors.toList());

        if (annualIncomeReports.size() >= 3 && annualCashFlowReports.size() >= 3) {
            double averageReinvestmentRate = 0.0;
            int validYears = 0;

            for (int i = 0; i < 3; i++) {
                IncomeReport incomeReport = annualIncomeReports.get(i);
                CashFlowReport cashFlowReport = annualCashFlowReports.get(i);

                BigDecimal revenue = safeParser.parse(incomeReport.getRevenue());
                BigDecimal capitalExpenditure = safeParser.parse(cashFlowReport.getCapitalExpenditure());
                BigDecimal depreciation = safeParser.parse(cashFlowReport.getDepreciationAndAmortization());
                BigDecimal changeInWorkingCapital = safeParser.parse(cashFlowReport.getChangeInWorkingCapital());

                // Net Reinvestment = (|CapEx| - Depreciation) + ChangeInWorkingCapital
                BigDecimal grossReinvestment = capitalExpenditure.abs().subtract(depreciation);
                BigDecimal netReinvestment = grossReinvestment.add(changeInWorkingCapital);

                // Floor at zero to prevent negative reinvestment rates
                BigDecimal flooredReinvestment = netReinvestment.max(BigDecimal.ZERO);

                if (revenue.compareTo(BigDecimal.ZERO) != 0) {
                    double reinvestmentRate = flooredReinvestment.divide(revenue, 4, RoundingMode.HALF_UP).doubleValue();
                    averageReinvestmentRate += reinvestmentRate;
                    validYears++;
                }
            }

            if (validYears > 0) {
                growthUserInput.setReinvestmentAsPctOfRevenue((averageReinvestmentRate / validYears) * 100); // Convert to percentage
            } else {
                growthUserInput.setReinvestmentAsPctOfRevenue(0.0); // Default if no valid data
            }
        } else {
            growthUserInput.setReinvestmentAsPctOfRevenue(0.0); // Default if insufficient data
        }

        return growthUserInput;
    }


    public GrowthOutput calculateGrowthCompanyValuation(GrowthValuation growthValuation) {
        GrowthOutput calculatedOutput = growthValuationCalculator.calculateIntrinsicValue(
                growthValuation.getGrowthValuationData(),
                growthValuation.getGrowthUserInput()
        );

        return calculatedOutput;
    }

    public List<GrowthValuation> getGrowthCompanyValuationHistory(String ticker) {
        return valuationsRepository.findById(ticker)
                .map(Valuations::getGrowthValuations)
                .orElse(java.util.Collections.emptyList());
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
        DcfCalculationData.HistoricalAssumptions assumptions = getHistoricalAssumptions(ticker, income, companyOverviewOptional, meta, cashFlow);

        return DcfCalculationData.builder()
                .meta(meta)
                .income(income)
                .balanceSheet(balanceSheet)
                .cashFlow(cashFlow)
                .assumptions(assumptions)
                .build();
    }

    public void saveGrowthCompanyValuation(GrowthValuation growthValuation) {
        growthValuation.setValuationDate(LocalDateTime.now().toString());
        String ticker = growthValuation.getGrowthValuationData().getTicker();
        Valuations valuations = valuationsRepository.findById(ticker).orElse(new Valuations());
        valuations.setTicker(ticker);
        valuations.getGrowthValuations().add(growthValuation);
        valuationsRepository.save(valuations);
    }

    private DcfCalculationData.CompanyMeta getCompanyMeta(String ticker) {
        Optional<CompanyOverview> companyOverviewOptional = companyOverviewRepository.findBySymbol(ticker);
        Optional<GlobalQuote> globalQuoteOptional = stockQuotesRepository.findBySymbol(ticker)
                .flatMap(quotes -> quotes.getQuotes().stream().findFirst());

        List<IncomeReport> quarterlyReports = incomeStatementRepository.findBySymbol(ticker)
                .map(IncomeStatementData::getQuarterlyReports)
                .orElse(List.of());

        // Sort by date descending and take the last 4 for TTM
        List<IncomeReport> lastQuarterlyReport = quarterlyReports.stream()
                .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                .limit(1)
                .collect(Collectors.toList());

        String companyName = companyOverviewOptional.map(CompanyOverview::getCompanyName).orElse("N/A");
        String currency = companyOverviewOptional.map(CompanyOverview::getCurrency).orElse("USD");
        BigDecimal currentSharePrice = globalQuoteOptional.map(gq -> safeParser.parse(gq.getAdjClose())).orElse(BigDecimal.ZERO);

        BigDecimal sharesOutstanding = null;
        if (!lastQuarterlyReport.isEmpty()) {

            sharesOutstanding = safeParser.parse(lastQuarterlyReport.getFirst().getWeightedAverageShsOut());
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
                    .operatingCashFlow(BigDecimal.ZERO)
                    .depreciationAndAmortization(BigDecimal.ZERO)
                    .capitalExpenditure(BigDecimal.ZERO)
                    .stockBasedCompensation(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal operatingCashFlow = ttmReports.stream()
                .map(CashFlowReport::getOperatingCashFlow)
                .map(safeParser::parse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
                .operatingCashFlow(operatingCashFlow)
                .depreciationAndAmortization(depreciationAndAmortization)
                .capitalExpenditure(capitalExpenditure)
                .stockBasedCompensation(stockBasedCompensation)
                .build();
    }

    private DcfCalculationData.HistoricalAssumptions getHistoricalAssumptions(String ticker, DcfCalculationData.IncomeData incomeData, Optional<CompanyOverview> companyOverviewOptional, DcfCalculationData.CompanyMeta meta, DcfCalculationData.CashFlowData cashFlow) {
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

        double fcfGrowthRateAverage3Year = calculateFcfGrowthRateAverageLast3Years(ticker);

        BigDecimal ttmFcf = cashFlow.operatingCashFlow().subtract(cashFlow.capitalExpenditure().abs());
        BigDecimal marketCap = meta.currentSharePrice().multiply(meta.sharesOutstanding());
        double marketCapToFcfMultiple = 0.0;
        if (ttmFcf.compareTo(BigDecimal.ZERO) != 0) {
            marketCapToFcfMultiple = marketCap.divide(ttmFcf, 2, BigDecimal.ROUND_HALF_UP).doubleValue();
        }

        return DcfCalculationData.HistoricalAssumptions.builder()
                .beta(beta)
                .riskFreeRate(riskFreeRate)
                .marketRiskPremium(marketRiskPremium)
                .effectiveTaxRate(effectiveTaxRate)
                .revenueGrowthCagr3Year(revenueGrowthCagr3Year)
                .averageEbitMargin3Year(averageEbitMargin3Year)
                .fcfGrowthRate(fcfGrowthRateAverage3Year)
                .marketCapToFcfMultiple(marketCapToFcfMultiple)
                .build();
    }

    private double calculateFcfGrowthRateAverageLast3Years(String ticker) {
        List<CashFlowReport> annualReports = cashFlowRepository.findBySymbol(ticker)
                .map(CashFlowData::getAnnualReports)
                .orElse(List.of());

        List<CashFlowReport> sortedAnnualReports = annualReports.stream()
                .sorted(Comparator.comparing(CashFlowReport::getDate).reversed())
                .toList();

        if (sortedAnnualReports.size() < 4) {
            return 0.0;
        }

        List<BigDecimal> fcfValues = sortedAnnualReports.subList(0, 4).stream()
                .map(report -> {
                    BigDecimal operatingCashFlow = safeParser.parse(report.getOperatingCashFlow());
                    BigDecimal capitalExpenditure = safeParser.parse(report.getCapitalExpenditure());

                    return operatingCashFlow.subtract(capitalExpenditure.abs());
                })
                .toList();

        List<Double> growthRates = new ArrayList<>();

        // helper
        for (int i = 0; i < 3; i++) {
            BigDecimal newer = fcfValues.get(i);       // Year 0, -1, -2
            BigDecimal older = fcfValues.get(i + 1);   // Year -1, -2, -3

            if (older.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal denominator = older.abs(); // key fix
            BigDecimal growth = newer.subtract(older)
                    .divide(denominator, 6, RoundingMode.HALF_UP);

            growthRates.add(growth.doubleValue());
        }

        if (growthRates.isEmpty()) {
            return 0.0;
        }

        return growthRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}