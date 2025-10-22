package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.*;
import com.testehan.finana.util.DateUtils;
import com.testehan.finana.util.FinancialRatiosCalculator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinancialDataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialDataService.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AlphaVantageService alphaVantageService;
    private final FMPService fmpService;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final EarningsHistoryRepository earningsHistoryRepository;
    private final StockQuotesRepository stockQuotesRepository;
    private final EarningsEstimatesRepository earningsEstimatesRepository;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final GeneratedReportRepository generatedReportRepository;
    private final RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    private final RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository;
    private final IndexQuotesRepository indexQuotesRepository;

    private final FinancialRatiosCalculator financialRatiosCalculator;

    private final DateUtils dateUtils;

    private final CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository;
    private final SecFilingService secFilingService;


    public FinancialDataService(AlphaVantageService alphaVantageService, FMPService fmpService, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository, CashFlowRepository cashFlowRepository, CompanyOverviewRepository companyOverviewRepository, EarningsHistoryRepository earningsHistoryRepository, StockQuotesRepository stockQuotesRepository, CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository, DateUtils dateUtils, EarningsEstimatesRepository earningsEstimatesRepository, FinancialRatiosRepository financialRatiosRepository, GeneratedReportRepository generatedReportRepository, RevenueSegmentationDataRepository revenueSegmentationDataRepository, RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository, IndexQuotesRepository indexQuotesRepository, FinancialRatiosCalculator financialRatiosCalculator, SecFilingService secFilingService) {
        this.alphaVantageService = alphaVantageService;
        this.fmpService = fmpService;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.companyOverviewRepository = companyOverviewRepository;
        this.earningsHistoryRepository = earningsHistoryRepository;
        this.stockQuotesRepository = stockQuotesRepository;
        this.companyEarningsTranscriptsRepository = companyEarningsTranscriptsRepository;
        this.dateUtils = dateUtils;
        this.earningsEstimatesRepository = earningsEstimatesRepository;
        this.financialRatiosRepository = financialRatiosRepository;
        this.generatedReportRepository = generatedReportRepository;
        this.revenueSegmentationDataRepository = revenueSegmentationDataRepository;
        this.revenueGeographicSegmentationRepository = revenueGeographicSegmentationRepository;
        this.indexQuotesRepository = indexQuotesRepository;
        this.financialRatiosCalculator = financialRatiosCalculator;
        this.secFilingService = secFilingService;
    }

    @NotNull
    public String getLatestReportedDate(String ticker) {
        var incomeData = getIncomeStatements(ticker).block();
        var latestIncomeReport = incomeData.getQuarterlyReports().stream().max(Comparator.comparing(report -> parseDate(report.getDate(), formatter))).get();
        return latestIncomeReport.getDate();
    }


    public Mono<QuarterlyEarningsTranscript> getEarningsCallTranscript(String symbol, String quarter) {
        return Mono.defer(() -> {
            Optional<CompanyEarningsTranscripts> earningsCallTranscriptFromDb = companyEarningsTranscriptsRepository.findById(symbol.toUpperCase());
            if (earningsCallTranscriptFromDb.isPresent()) {
                Optional<QuarterlyEarningsTranscript> quarterlyTranscript = earningsCallTranscriptFromDb.get().getTranscripts().stream()
                        .filter(transcript -> {if (Objects.nonNull(transcript.getQuarter())) {
                            return transcript.getQuarter().equals(quarter);
                        } else {
                            return false;
                        }}).findFirst();

                if (quarterlyTranscript.isPresent()) {
                    return Mono.just(quarterlyTranscript.get());
                }
            }

            return alphaVantageService.fetchEarningsCallTranscriptFromApiAndSave(symbol.toUpperCase(), quarter)
                    .flatMap(companyTranscripts -> companyTranscripts.getTranscripts().stream()
                            .filter(transcript -> {if (Objects.nonNull(transcript.getQuarter())) {
                                return transcript.getQuarter().equals(quarter);
                            } else {
                                return false;
                            }})
                            .findFirst()
                            .map(Mono::just)
                            .orElse(Mono.empty()));
        });
    }

    public Mono<EarningsHistory> getEarningsHistory(String symbol) {
        return Mono.defer(() -> {
            Optional<EarningsHistory> earningsHistoryFromDb = earningsHistoryRepository.findBySymbol(symbol.toUpperCase());
            if (earningsHistoryFromDb.isPresent()) {
                return Mono.just(earningsHistoryFromDb.get());
            } else {
                return fmpService.fetchEarningsHistory(symbol.toUpperCase())
                        .flatMap(earningsHistory -> Mono.just(earningsHistoryRepository.save(earningsHistory)));
            }
        });
    }

    public Mono<EarningsEstimate> getEarningsEstimates(String symbol) {
        return Mono.defer(() -> {
            Optional<EarningsEstimate> earningsEstimateFromDb = earningsEstimatesRepository.findBySymbol(symbol.toUpperCase());
            if (earningsEstimateFromDb.isPresent() && isRecent(earningsEstimateFromDb.get().getLastUpdated(), 10080)) {
                return Mono.just(earningsEstimateFromDb.get());
            } else {
                return fmpService.fetchAnalystEstimates(symbol.toUpperCase())
                        .flatMap(estimates -> {
                            EarningsEstimate earningsEstimate = new EarningsEstimate();
                            earningsEstimate.setSymbol(symbol.toUpperCase());
                            earningsEstimate.setEstimates(estimates);
                            earningsEstimate.setLastUpdated(LocalDateTime.now());
                            return Mono.just(earningsEstimatesRepository.save(earningsEstimate));
                        });
            }
        });
    }

    public Mono<GlobalQuote> getLastStockQuote(String symbol) {
        return Mono.defer(() -> {
            Optional<StockQuotes> stockQuotesFromDb = stockQuotesRepository.findBySymbol(symbol);
            if (stockQuotesFromDb.isPresent() && !stockQuotesFromDb.get().getQuotes().isEmpty() && isRecent(stockQuotesFromDb.get().getLastUpdated(), 100)) {
                return Mono.just(stockQuotesFromDb.get());
            } else {
                return fmpService.getHistoricalDividendAdjustedEodPrice(symbol)
                        .flatMap(globalQuotes -> {
                            StockQuotes stockQuotes;
                            if (stockQuotesFromDb.isPresent()) {
                                stockQuotes = stockQuotesFromDb.get();
                                stockQuotes.setQuotes(globalQuotes);
                            } else {
                                stockQuotes = new StockQuotes();
                                stockQuotes.setSymbol(symbol.toUpperCase());
                                stockQuotes.setQuotes(globalQuotes);
                            }
                            stockQuotes.setLastUpdated(LocalDateTime.now());
                            return Mono.just(stockQuotesRepository.save(stockQuotes));
                        });
            }
        }).map(stockQuotes -> {
            List<GlobalQuote> quotes = stockQuotes.getQuotes();
            if (quotes != null && !quotes.isEmpty()) {
                return stockQuotesRepository.findLastQuoteBySymbol(symbol).get();
            }
            return null;
        }).filter(Objects::nonNull);
    }

    public Mono<IndexQuotes> getIndexQuotes(String symbol) {
        return Mono.defer(() -> {
            Optional<IndexQuotes> indexQuotesFromDb = indexQuotesRepository.findById(symbol.toUpperCase());
            if (indexQuotesFromDb.isPresent() && isRecent(indexQuotesFromDb.get().getLastUpdated(), 100)) {
                return Mono.just(indexQuotesFromDb.get());
            } else {
                return fmpService.getIndexHistoricalData(symbol)
                        .flatMap(indexDataList -> {
                            IndexQuotes indexQuotes;
                            if (indexQuotesFromDb.isPresent()) {
                                indexQuotes = indexQuotesFromDb.get();
                                indexQuotes.setQuotes(indexDataList);
                            } else {
                                indexQuotes = new IndexQuotes(symbol.toUpperCase(), indexDataList);
                            }
                            indexQuotes.setLastUpdated(LocalDateTime.now());
                            return Mono.just(indexQuotesRepository.save(indexQuotes));
                        });
            }
        });
    }

    public Optional<GlobalQuote> getStockQuoteByDate(String symbol, LocalDate date) {
        for (int i = 0; i < 7; i++) {       // because some days are weekends or holidays when the market is closed
            LocalDate lookupDate = date.minusDays(i);
            String dateStr = lookupDate.format(formatter);
            Optional<GlobalQuote> quote = stockQuotesRepository.findQuoteBySymbolAndDate(symbol.toUpperCase(), dateStr);
            if (quote.isPresent()) {
                return quote;
            }
        }
        return Optional.empty();
    }

    public Optional<IndexData> getIndexQuoteByDate(String symbol, LocalDate date) {
        for (int i = 0; i < 7; i++) {   // because some days are weekends or holidays when the market is not opened
            LocalDate lookupDate = date.minusDays(i);
            String dateStr = lookupDate.format(formatter);
            Optional<IndexData> quote = indexQuotesRepository.findQuoteBySymbolAndDate(symbol.toUpperCase(), dateStr);
            if (quote.isPresent()) {
                return quote;
            }
        }
        return Optional.empty();
    }

    public Optional<GlobalQuote> getFirstStockQuote(String symbol) {
        return stockQuotesRepository.findFirstQuoteBySymbol(symbol.toUpperCase());
    }

    public Optional<IndexData> getFirstIndexQuote(String symbol) {
        return indexQuotesRepository.findFirstQuoteBySymbol(symbol.toUpperCase());
    }

    public Optional<IndexData> getLastIndexQuote(String symbol) {
        return indexQuotesRepository.findLastQuoteBySymbol(symbol.toUpperCase());
    }

    public Mono<List<CompanyOverview>> getCompanyOverview(String symbol) {
        return Mono.defer(() -> {
            Optional<CompanyOverview> overviewFromDb = companyOverviewRepository.findBySymbol(symbol.toUpperCase());
            if (overviewFromDb.isPresent() && isRecent(overviewFromDb.get().getLastUpdated(), 10080)) {
                return Mono.just(List.of(overviewFromDb.get()));
            } else {
                return fmpService.getCompanyOverview(symbol.toUpperCase(), overviewFromDb)
                        .flatMap(overview -> Mono.just(List.of(companyOverviewRepository.save(overview))));
            }
        });
    }

    public Mono<IncomeStatementData> getIncomeStatements(String symbol) {
        return Mono.defer(() -> {
            Optional<IncomeStatementData> incomeStatementsFromDb = incomeStatementRepository.findBySymbol(symbol.toUpperCase());
            if (incomeStatementsFromDb.isPresent()) {
                return Mono.just(incomeStatementsFromDb.get());
            } else {
                IncomeStatementData incomeStatementData = new IncomeStatementData();
                incomeStatementData.setSymbol(symbol);
                incomeStatementData.setAnnualReports(fmpService.getIncomeStatement(symbol.toUpperCase(),"annual").block());
                incomeStatementData.setQuarterlyReports(fmpService.getIncomeStatement(symbol.toUpperCase(),"quarter").block());

                return Mono.just(incomeStatementRepository.save(incomeStatementData));
            }
        });
    }

    public Mono<BalanceSheetData> getBalanceSheet(String symbol) {
        return Mono.defer(() -> {
            Optional<BalanceSheetData> balanceSheetFromDb = balanceSheetRepository.findBySymbol(symbol.toUpperCase());
            if (balanceSheetFromDb.isPresent()) {
                return Mono.just(balanceSheetFromDb.get());
            } else {
                BalanceSheetData balanceSheetData = new BalanceSheetData();
                balanceSheetData.setSymbol(symbol);
                balanceSheetData.setAnnualReports(fmpService.getBalanceSheetStatement(symbol.toUpperCase(),"annual").block());
                balanceSheetData.setQuarterlyReports(fmpService.getBalanceSheetStatement(symbol.toUpperCase(),"quarter").block());

                return Mono.just(balanceSheetRepository.save(balanceSheetData));
            }
        });
    }

    public Mono<CashFlowData> getCashFlow(String symbol) {
        return Mono.defer(() -> {
            Optional<CashFlowData> cashFlowFromDb = cashFlowRepository.findBySymbol(symbol.toUpperCase());
            if (cashFlowFromDb.isPresent()) {
                return Mono.just(cashFlowFromDb.get());
            } else {
                CashFlowData cashFlowData = new CashFlowData();
                cashFlowData.setSymbol(symbol);
                cashFlowData.setAnnualReports(fmpService.getCashflowStatement(symbol.toUpperCase(),"annual").block());
                cashFlowData.setQuarterlyReports(fmpService.getCashflowStatement(symbol.toUpperCase(),"quarter").block());

                return Mono.just(cashFlowRepository.save(cashFlowData));
            }
        });
    }

    public Mono<RevenueSegmentationData> getRevenueSegmentation(String symbol) {
        return Mono.defer(() -> {
            Optional<RevenueSegmentationData> revenueSegmentationFromDb = revenueSegmentationDataRepository.findBySymbol(symbol.toUpperCase());
            if (revenueSegmentationFromDb.isPresent()) {
                return Mono.just(revenueSegmentationFromDb.get());
            } else {
                RevenueSegmentationData revenueSegmentationData = new RevenueSegmentationData();
                revenueSegmentationData.setSymbol(symbol);
                revenueSegmentationData.setAnnualReports(fmpService.getRevenueSegmentation(symbol.toUpperCase(),"annual").block());
                // below is part of the paid plan annual subscription for this API...yearly info is good enough
//                revenueSegmentationData.setQuarterlyReports(fmpService.getRevenueSegmentation(symbol.toUpperCase(),"quarter").block());

                return Mono.just(revenueSegmentationDataRepository.save(revenueSegmentationData));
            }
        });
    }

    public Mono<RevenueGeographicSegmentationData> getRevenueGeographicSegmentation(String symbol) {
        return Mono.defer(() -> {
            Optional<RevenueGeographicSegmentationData> revenueGeographicSegmentationFromDb = revenueGeographicSegmentationRepository.findBySymbol(symbol.toUpperCase());
            if (revenueGeographicSegmentationFromDb.isPresent()) {
                return Mono.just(revenueGeographicSegmentationFromDb.get());
            } else {
                RevenueGeographicSegmentationData revenueGeographicSegmentationData = new RevenueGeographicSegmentationData();
                revenueGeographicSegmentationData.setSymbol(symbol);
                revenueGeographicSegmentationData.setReports(fmpService.getRevenueGeographicSegmentation(symbol.toUpperCase(),"annual").block());

                return Mono.just(revenueGeographicSegmentationRepository.save(revenueGeographicSegmentationData));
            }
        });
    }

    private boolean isRecent(LocalDateTime lastUpdated, int minutes) {
        if (lastUpdated == null) {
            return false;
        }
        return ChronoUnit.MINUTES.between(lastUpdated, LocalDateTime.now()) < minutes;
    }

    public void deleteFinancialData(String symbol) {
        String upperCaseSymbol = symbol.toUpperCase();

        balanceSheetRepository.deleteBySymbol(upperCaseSymbol);
        cashFlowRepository.deleteBySymbol(upperCaseSymbol);
        companyEarningsTranscriptsRepository.deleteById(upperCaseSymbol);
        companyOverviewRepository.deleteBySymbol(upperCaseSymbol);
        earningsHistoryRepository.deleteBySymbol(upperCaseSymbol);
        incomeStatementRepository.deleteBySymbol(upperCaseSymbol);
        stockQuotesRepository.deleteBySymbol(upperCaseSymbol);
        earningsEstimatesRepository.deleteBySymbol(upperCaseSymbol);
        financialRatiosRepository.deleteBySymbol(upperCaseSymbol);
        generatedReportRepository.deleteBySymbol(upperCaseSymbol);
        revenueGeographicSegmentationRepository.deleteBySymbol(upperCaseSymbol);
        revenueSegmentationDataRepository.deleteBySymbol(upperCaseSymbol);
        secFilingService.deleteSecFilings(upperCaseSymbol);

        LOGGER.info("Deleted all financial data for ticker: {}", upperCaseSymbol);
    }

    private LocalDate parseDate(String dateStr, DateTimeFormatter formatter) {
        return LocalDate.parse(dateStr, formatter);
    }

    public Optional<FinancialRatiosData> getFinancialRatios(String symbol) {
        Optional<FinancialRatiosData> existingRatiosData = financialRatiosRepository.findBySymbol(symbol);

        if (existingRatiosData.isEmpty()) {
            FinancialRatiosData newRatiosData = calculateAndSaveRatios(symbol);
            return Optional.ofNullable(newRatiosData); // Return newly calculated data
        }
        return existingRatiosData;
    }

    private FinancialRatiosData calculateAndSaveRatios(String symbol) {
        CompanyOverview companyOverview = getCompanyOverview(symbol).block().getFirst();
        Optional<IncomeStatementData> incomeStatementDataOptional = getIncomeStatements(symbol).blockOptional();
        Optional<BalanceSheetData> balanceSheetDataOptional = getBalanceSheet(symbol).blockOptional();
        Optional<CashFlowData> cashFlowDataOptional = getCashFlow(symbol).blockOptional();

        if (incomeStatementDataOptional.isPresent() && balanceSheetDataOptional.isPresent()
                && cashFlowDataOptional.isPresent()) {
            IncomeStatementData incomeStatementData = incomeStatementDataOptional.get();
            BalanceSheetData balanceSheetData = balanceSheetDataOptional.get();
            CashFlowData cashFlowData = cashFlowDataOptional.get();

            FinancialRatiosData financialRatiosData = financialRatiosRepository.findBySymbol(symbol)
                    .orElse(new FinancialRatiosData());
            financialRatiosData.setSymbol(symbol);
            financialRatiosData.setAnnualReports(new ArrayList<>());
            financialRatiosData.setQuarterlyReports(new ArrayList<>());

            // Process Annual Reports
            processAndAddReports(symbol, companyOverview, incomeStatementData.getAnnualReports(), balanceSheetData.getAnnualReports(), cashFlowData.getAnnualReports(), financialRatiosData.getAnnualReports()); // Update this
            // Process Quarterly Reports
            processAndAddReports(symbol, companyOverview, incomeStatementData.getQuarterlyReports(), balanceSheetData.getQuarterlyReports(), cashFlowData.getQuarterlyReports(), financialRatiosData.getQuarterlyReports()); // Update this

            return financialRatiosRepository.save(financialRatiosData);
        }
        return null; // Or throw an exception if data is not found
    }

    private void processAndAddReports(String symbol,
                                      CompanyOverview companyOverview,
                                      List<IncomeReport> incomeReports,
                                      List<BalanceSheetReport> balanceSheetReports,
                                      List<CashFlowReport> cashFlowReports,
                                      List<FinancialRatiosReport> targetList)
    {

        Map<String, BalanceSheetReport> balanceSheetMap = balanceSheetReports.stream()
                .collect(Collectors.toMap(BalanceSheetReport::getDate, Function.identity(), (a, b) -> a));

        Map<String, CashFlowReport> cashFlowMap = cashFlowReports.stream()
                .collect(Collectors.toMap(CashFlowReport::getDate, Function.identity(), (a, b) -> a));

        for (IncomeReport incomeReport : incomeReports) {
            String fiscalDateEnding = incomeReport.getDate();

            // Find corresponding reports
            BalanceSheetReport balanceSheet = balanceSheetMap.get(fiscalDateEnding);
            CashFlowReport cashFlow = cashFlowMap.get(fiscalDateEnding);

            if (balanceSheet == null || cashFlow == null) {
                continue; // Skip if we don't have all required reports
            }

            FinancialRatiosReport ratios = financialRatiosCalculator.calculateRatios(companyOverview, incomeReport, balanceSheet, cashFlow);
            targetList.add(ratios);

        }
    }

    public void ensureFinancialDataIsPresent(String ticker) {
        this.getLastStockQuote(ticker).block();
        this.getIndexQuotes("^GSPC").block();
        this.getIncomeStatements(ticker).block();
        this.getBalanceSheet(ticker).block();
        this.getCashFlow(ticker).block();
        this.getEarningsEstimates(ticker).block();
        this.getEarningsHistory(ticker).block();
        this.getCompanyOverview(ticker).block().get(0);
        this.getRevenueSegmentation(ticker).block();
        this.getRevenueGeographicSegmentation(ticker).block();
        secFilingService.fetchAndSaveSecFilings(ticker);
        secFilingService.getAndSaveSecFilings(ticker);

        this.getFinancialRatios(ticker);

        this.updateFinancialRatiosFromFmp(ticker);
        this.updateTtmFinancialRatios(ticker);

        var latestReportDate = this.getLatestReportedDate(ticker);

        if (latestReportDate != null) {
            String dateQuarter = dateUtils.getDateQuarter(latestReportDate);
            this.getEarningsCallTranscript(ticker, dateQuarter).block();
        }
    }

    private void updateTtmFinancialRatios(String ticker) {
        fmpService.getFinancialRatiosTtm(ticker)
                .map(fmpRatios -> {
                    FinancialRatiosData data = financialRatiosRepository.findBySymbol(ticker).orElse(new FinancialRatiosData());
                    data.setSymbol(ticker);

                    FinancialRatiosReport report = new FinancialRatiosReport();
                    report.setDate(fmpRatios.getDate());

                    if (fmpRatios.getPriceToEarningsRatioTTM() != null) {
                        report.setPeRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToEarningsRatioTTM()));
                    }
                    if (fmpRatios.getPriceToEarningsGrowthRatioTTM() != null) {
                        report.setPriceToEarningsGrowthRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToEarningsGrowthRatioTTM()));
                    }
                    if (fmpRatios.getForwardPriceToEarningsGrowthRatioTTM() != null) {
                        report.setForwardPriceToEarningsGrowthRatio(java.math.BigDecimal.valueOf(fmpRatios.getForwardPriceToEarningsGrowthRatioTTM()));
                    }
                    if (fmpRatios.getPriceToBookRatioTTM() != null) {
                        report.setPbRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToBookRatioTTM()));
                    }
                    if (fmpRatios.getPriceToSalesRatioTTM() != null) {
                        report.setPriceToSalesRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToSalesRatioTTM()));
                    }
                    if (fmpRatios.getPriceToFreeCashFlowRatioTTM() != null) {
                        report.setPfcfRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToFreeCashFlowRatioTTM()));
                    }
                    if (fmpRatios.getPriceToOperatingCashFlowRatioTTM() != null) {
                        report.setPocfratio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToOperatingCashFlowRatioTTM()));
                    }
                    if (fmpRatios.getPriceToFairValueTTM() != null) {
                        report.setPriceToFairValue(java.math.BigDecimal.valueOf(fmpRatios.getPriceToFairValueTTM()));
                    }
                    if (fmpRatios.getEnterpriseValueMultipleTTM() != null) {
                        report.setEnterpriseValueMultiple(java.math.BigDecimal.valueOf(fmpRatios.getEnterpriseValueMultipleTTM()));
                    }
                    data.setTtmReport(report);
                    return data;
                })
                .doOnSuccess(financialRatiosRepository::save)
                .doOnError(e -> LOGGER.error("Error with TTM financial ratios for " + ticker, e)).block();
    }


    private void updateFinancialRatiosFromFmp(String ticker) {
        fmpService.getFinancialRatios(ticker)
                .map(reports -> { // reports is List<FmpRatios>
                    FinancialRatiosData data = financialRatiosRepository.findBySymbol(ticker).orElse(new FinancialRatiosData());

                    data.setSymbol(ticker);
                    List<FinancialRatiosReport> annualReports = data.getAnnualReports();
                    if (annualReports == null) {
                        annualReports = new ArrayList<>();
                        data.setAnnualReports(annualReports);
                    }

                    Map<String, FinancialRatiosReport> reportsByDate = annualReports.stream()
                            .collect(Collectors.toMap(FinancialRatiosReport::getDate, java.util.function.Function.identity()));

                    for (FmpRatios fmpRatios : reports) {
                        FinancialRatiosReport report = reportsByDate.get(fmpRatios.getDate());

                        if (report == null) {
                            report = new FinancialRatiosReport();
                            report.setDate(fmpRatios.getDate());
                            annualReports.add(report);
                        }

                        if (fmpRatios.getPriceToEarningsRatio() != null) {
                            report.setPeRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToEarningsRatio()));
                        }
                        if (fmpRatios.getPriceToEarningsGrowthRatio() != null) {
                            report.setPriceToEarningsGrowthRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToEarningsGrowthRatio()));
                        }
                        if (fmpRatios.getForwardPriceToEarningsGrowthRatio() != null) {
                            report.setForwardPriceToEarningsGrowthRatio(java.math.BigDecimal.valueOf(fmpRatios.getForwardPriceToEarningsGrowthRatio()));
                        }
                        if (fmpRatios.getPriceToBookRatio() != null) {
                            report.setPbRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToBookRatio()));
                        }
                        if (fmpRatios.getPriceToSalesRatio() != null) {
                            report.setPriceToSalesRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToSalesRatio()));
                        }
                        if (fmpRatios.getPriceToFreeCashFlowRatio() != null) {
                            report.setPfcfRatio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToFreeCashFlowRatio()));
                        }
                        if (fmpRatios.getPriceToOperatingCashFlowRatio() != null) {
                            report.setPocfratio(java.math.BigDecimal.valueOf(fmpRatios.getPriceToOperatingCashFlowRatio()));
                        }
                        if (fmpRatios.getPriceToFairValue() != null) {
                            report.setPriceToFairValue(java.math.BigDecimal.valueOf(fmpRatios.getPriceToFairValue()));
                        }
                        if (fmpRatios.getEnterpriseValueMultiple() != null) {
                            report.setEnterpriseValueMultiple(java.math.BigDecimal.valueOf(fmpRatios.getEnterpriseValueMultiple()));
                        }
                    }

                    return data;
                })
                .doOnSuccess(financialRatiosRepository::save)
                .doOnError(e -> LOGGER.error("Error with financial ratios for " + ticker, e)).block();
    }

    public FinancialDataAvailability checkFinancialDataAvailability(String ticker) {
        FinancialDataAvailability availability = new FinancialDataAvailability();
        String upperCaseTicker = ticker.toUpperCase();

        availability.setLastStockQuote(stockQuotesRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setIncomeStatements(incomeStatementRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setBalanceSheet(balanceSheetRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setCashFlow(cashFlowRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setEarningsEstimates(earningsEstimatesRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setEarningsHistory(earningsHistoryRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setCompanyOverview(companyOverviewRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setRevenueSegmentation(revenueSegmentationDataRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setRevenueGeographicSegmentation(revenueGeographicSegmentationRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setFinancialRatios(financialRatiosRepository.findBySymbol(upperCaseTicker).isPresent());
        availability.setEarningsCallTranscript(companyEarningsTranscriptsRepository.findById(upperCaseTicker).isPresent());
        availability.setSecQuarterlyFilings(secFilingService.hasTenQFilings(upperCaseTicker));
        availability.setSecAnnualFilings(secFilingService.hasTenKFilings(upperCaseTicker));

        return availability;
    }

    public List<CompanyOverview> findAllCompanyOverview() {
        return companyOverviewRepository.findAll();
    }
}
