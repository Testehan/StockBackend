package com.testehan.finana.service.valuation;

import com.testehan.finana.model.*;
import com.testehan.finana.model.valuation.Valuations;
import com.testehan.finana.model.valuation.growth.*;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.FMPService;
import com.testehan.finana.service.valuation.growth.GrowthValuationCalculator;
import com.testehan.finana.util.SafeParser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

;

@Service
public class GrowthValuationService extends BaseValuationService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final GrowthValuationCalculator growthValuationCalculator;

    public GrowthValuationService(CompanyOverviewRepository companyOverviewRepository,
                                  StockQuotesRepository stockQuotesRepository,
                                  IncomeStatementRepository incomeStatementRepository,
                                  BalanceSheetRepository balanceSheetRepository,
                                  CashFlowRepository cashFlowRepository,
                                  ValuationsRepository valuationsRepository,
                                  FMPService fmpService,
                                  SafeParser safeParser,
                                  GrowthValuationCalculator growthValuationCalculator) {
        super(companyOverviewRepository, stockQuotesRepository, incomeStatementRepository,
                balanceSheetRepository, cashFlowRepository, valuationsRepository, fmpService, safeParser);
        this.growthValuationCalculator = growthValuationCalculator;
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
            growthValuationData.setCurrentSharePrice(safeParser.parse(quote.getAdjClose()));
        });
        // Risk-free rate can be hardcoded for now, or fetched from a config
        growthValuationData.setRiskFreeRate(BigDecimal.valueOf(0.042)); // Example: 4.2% - 10Y US Treasury yield

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
            year.setRevenue(safeParser.parse(report.getRevenue()));
            year.setOperatingIncome(safeParser.parse(report.getOperatingIncome()));
            year.setPretaxIncome(safeParser.parse(report.getIncomeBeforeTax()));
            year.setNetIncome(safeParser.parse(report.getNetIncome()));
            incomeStatementYears.add(year);
        }
        growthValuationData.setIncomeStatements(incomeStatementYears);

        // Process Balance Sheets
        List<BalanceSheetYear> balanceSheetYears = new ArrayList<>();
        for (BalanceSheetReport report : annualBalanceSheetReports) {
            BalanceSheetYear year = new BalanceSheetYear();
            year.setFiscalYear(LocalDate.parse(report.getDate()).getYear());
            year.setCashAndEquivalents(safeParser.parse(report.getCashAndCashEquivalents()));
            year.setShortTermDebt(safeParser.parse(report.getShortTermDebt()));
            year.setLongTermDebt(safeParser.parse(report.getLongTermDebt()));
            year.setTotalAssets(safeParser.parse(report.getTotalAssets()));
            year.setTotalEquity(safeParser.parse(report.getTotalEquity()));
            balanceSheetYears.add(year);
        }
        growthValuationData.setBalanceSheets(balanceSheetYears);

        // Process Cash Flows
        List<CashFlowYear> cashFlowYears = new ArrayList<>();
        for (CashFlowReport report : annualCashFlowReports) {
            CashFlowYear year = new CashFlowYear();
            year.setFiscalYear(LocalDate.parse(report.getDate()).getYear());
            year.setOperatingCashFlow(safeParser.parse(report.getOperatingCashFlow()));
            year.setCapitalExpenditures(safeParser.parse(report.getCapitalExpenditure()));
            year.setDepreciationAndAmortization(safeParser.parse(report.getDepreciationAndAmortization()));
            year.setChangeInWorkingCapital(safeParser.parse(report.getChangeInWorkingCapital()));
            cashFlowYears.add(year);
        }
        growthValuationData.setCashFlows(cashFlowYears);

        // Tax Attributes (Placeholder - actual data source needed)
        growthValuationData.setNetOperatingLossCarryforward(BigDecimal.ZERO);
        growthValuationData.setNolExpirationYears(0);

        // Capital Structure and Share Counts (from latest reports)
        annualBalanceSheetReports.stream().max(Comparator.comparing(BalanceSheetReport::getDate)).ifPresent(latestReport -> {
            BigDecimal latestShortTermDebt = safeParser.parse(latestReport.getShortTermDebt());
            BigDecimal latestLongTermDebt = safeParser.parse(latestReport.getLongTermDebt());
            BigDecimal latestTotalDebt = latestShortTermDebt.add(latestLongTermDebt);

            growthValuationData.setTotalDebt(latestTotalDebt);
            growthValuationData.setCashBalance(safeParser.parse(latestReport.getCashAndCashEquivalents()));

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
                    BigDecimal previousShortTermDebt = safeParser.parse(previousReport.getShortTermDebt());
                    BigDecimal previousLongTermDebt = safeParser.parse(previousReport.getLongTermDebt());
                    BigDecimal previousTotalDebt = previousShortTermDebt.add(previousLongTermDebt);

                    BigDecimal averageDebt = latestTotalDebt.add(previousTotalDebt).divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
                    BigDecimal interestExpense = safeParser.parse(latestIncomeReport.getInterestExpense()).abs();

                    if (averageDebt.compareTo(BigDecimal.ZERO) != 0) {
                        growthValuationData.setAverageInterestRate(interestExpense.divide(averageDebt, MathContext.DECIMAL64));
                    }
                }
            }
        });

        // Share Counts
        annualIncomeReports.stream().max(Comparator.comparing(IncomeReport::getDate)).ifPresent(latestReport -> {
            growthValuationData.setCommonSharesOutstanding(safeParser.parse(latestReport.getWeightedAverageShsOut()));
        });

        // Calculate Market Capitalization
        if (growthValuationData.getCommonSharesOutstanding().compareTo(BigDecimal.ZERO) != 0 && growthValuationData.getCurrentSharePrice().compareTo(BigDecimal.ZERO) != 0) {
            growthValuationData.setMarketCapitalization(
                    growthValuationData.getCommonSharesOutstanding().multiply(growthValuationData.getCurrentSharePrice(), MathContext.DECIMAL64)
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
            BigDecimal pretaxIncome = safeParser.parse(latestReport.getIncomeBeforeTax());
            BigDecimal incomeTaxExpense = safeParser.parse(latestReport.getIncomeTaxExpense());
            if (pretaxIncome.compareTo(BigDecimal.ZERO) != 0) {
                growthUserInput.setMarginalTaxRate(incomeTaxExpense.divide(pretaxIncome, MathContext.DECIMAL64));
            } else {
                growthUserInput.setMarginalTaxRate(BigDecimal.valueOf(0.21)); // Default to US corporate tax rate
            }
        }

        // Calculate initialRevenueGrowthRate as 3-year CAGR if data is available
        if (annualIncomeReports.size() >= 3) {
            BigDecimal revenueYear0 = safeParser.parse(annualIncomeReports.get(0).getRevenue());
            BigDecimal revenueYear3 = safeParser.parse(annualIncomeReports.get(2).getRevenue());

            if (revenueYear3.compareTo(BigDecimal.ZERO) != 0) {
                // CAGR = (Revenue_Year0 / Revenue_YearN)^(1/N) - 1
                BigDecimal base = revenueYear0.divide(revenueYear3, MathContext.DECIMAL64);
                BigDecimal revenueGrowthCagr3Year = calculateNthRoot(base, 3).subtract(BigDecimal.ONE, MathContext.DECIMAL64);
                growthUserInput.setInitialRevenueGrowthRate(revenueGrowthCagr3Year.multiply(BigDecimal.valueOf(100), MathContext.DECIMAL64)); // Convert to percentage
            } else {
                growthUserInput.setInitialRevenueGrowthRate(BigDecimal.ZERO); // Default if year 3 revenue is zero
            }
        } else {
            growthUserInput.setInitialRevenueGrowthRate(BigDecimal.ZERO); // Default if insufficient data
        }

        // Calculate targetOperatingMargin as 3-year average operating margin if data is available
        if (annualIncomeReports.size() >= 3) {
            BigDecimal totalOperatingMargin = BigDecimal.ZERO;
            int count = 0;
            for (int i = 0; i < 3; i++) {
                IncomeReport report = annualIncomeReports.get(i);
                BigDecimal revenue = safeParser.parse(report.getRevenue());
                BigDecimal operatingIncome = safeParser.parse(report.getOperatingIncome());
                if (revenue.compareTo(BigDecimal.ZERO) != 0) {
                    totalOperatingMargin = totalOperatingMargin.add(operatingIncome.divide(revenue, MathContext.DECIMAL64), MathContext.DECIMAL64);
                    count++;
                }
            }
            if (count > 0) {
                growthUserInput.setTargetOperatingMargin(totalOperatingMargin.divide(BigDecimal.valueOf(count), MathContext.DECIMAL64).multiply(BigDecimal.valueOf(100), MathContext.DECIMAL64)); // Convert to percentage
            } else {
                growthUserInput.setTargetOperatingMargin(BigDecimal.ZERO);
            }
        } else {
            growthUserInput.setTargetOperatingMargin(BigDecimal.ZERO); // Default if insufficient data
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
            BigDecimal averageReinvestmentRate = BigDecimal.ZERO;
            int validYears = 0;

            for (int i = 0; i < 3; i++) {
                IncomeReport incomeReport = annualIncomeReports.get(i);
                CashFlowReport cashFlowReport = annualCashFlowReports.get(i);

                BigDecimal revenue = safeParser.parse(incomeReport.getRevenue());
                BigDecimal capitalExpenditure = safeParser.parse(cashFlowReport.getCapitalExpenditure());
                BigDecimal depreciation = safeParser.parse(cashFlowReport.getDepreciationAndAmortization());
                BigDecimal changeInWorkingCapital = safeParser.parse(cashFlowReport.getChangeInWorkingCapital());

                // Net Reinvestment = (|CapEx| - Depreciation) + ChangeInWorkingCapital
                BigDecimal grossReinvestment = capitalExpenditure.abs().subtract(depreciation, MathContext.DECIMAL64);
                BigDecimal netReinvestment = grossReinvestment.add(changeInWorkingCapital, MathContext.DECIMAL64);

                // Floor at zero to prevent negative reinvestment rates
                BigDecimal flooredReinvestment = netReinvestment.max(BigDecimal.ZERO);

                if (revenue.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal reinvestmentRate = flooredReinvestment.divide(revenue, MathContext.DECIMAL64);
                    averageReinvestmentRate = averageReinvestmentRate.add(reinvestmentRate, MathContext.DECIMAL64);
                    validYears++;
                }
            }

            if (validYears > 0) {
                growthUserInput.setReinvestmentAsPctOfRevenue(averageReinvestmentRate.divide(BigDecimal.valueOf(validYears), MathContext.DECIMAL64).multiply(BigDecimal.valueOf(100), MathContext.DECIMAL64)); // Convert to percentage
            } else {
                growthUserInput.setReinvestmentAsPctOfRevenue(BigDecimal.ZERO); // Default if no valid data
            }
        } else {
            growthUserInput.setReinvestmentAsPctOfRevenue(BigDecimal.ZERO); // Default if insufficient data
        }

        return growthUserInput;
    }

    public GrowthOutput calculateGrowthCompanyValuation(GrowthValuation growthValuation) {
        return growthValuationCalculator.calculateIntrinsicValue(
                growthValuation.getGrowthValuationData(),
                growthValuation.getGrowthUserInput()
        );
    }

    public List<GrowthValuation> getGrowthCompanyValuationHistory(String ticker) {
        return valuationsRepository.findById(ticker)
                .map(Valuations::getGrowthValuations)
                .orElse(java.util.Collections.emptyList());
    }

    public void saveGrowthCompanyValuation(GrowthValuation growthValuation) {
        growthValuation.setValuationDate(LocalDateTime.now().toString());
        String ticker = growthValuation.getGrowthValuationData().getTicker();
        Valuations valuations = valuationsRepository.findById(ticker).orElse(new Valuations());
        valuations.setTicker(ticker);
        valuations.getGrowthValuations().add(growthValuation);
        valuationsRepository.save(valuations);
    }

    private BigDecimal calculateNthRoot(BigDecimal base, int n) {
        if (base.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Base must be non-negative for real roots.");
        }
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal nBigDecimal = BigDecimal.valueOf(n);

        // Initial guess for the root using double approximation
        BigDecimal root = BigDecimal.valueOf(Math.pow(base.doubleValue(), 1.0 / n));

        for (int i = 0; i < 10; i++) { // Newton's method for approximation
            // x_new = (1/n) * ((n-1)*x_old + base / x_old^(n-1))
            BigDecimal powerOfRoot = root.pow(n - 1, MathContext.DECIMAL64);
            if (powerOfRoot.compareTo(BigDecimal.ZERO) == 0) { // Avoid division by zero
                throw new ArithmeticException("Division by zero in root calculation.");
            }
            root = BigDecimal.ONE.divide(nBigDecimal, MathContext.DECIMAL64)
                    .multiply(BigDecimal.valueOf(n - 1).multiply(root, MathContext.DECIMAL64)
                    .add(base.divide(powerOfRoot, MathContext.DECIMAL64), MathContext.DECIMAL64), MathContext.DECIMAL64);
        }
        return root;
    }
}
