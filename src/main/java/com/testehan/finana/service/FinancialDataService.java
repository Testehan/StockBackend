package com.testehan.finana.service;

import com.testehan.finana.model.BalanceSheetData;
import com.testehan.finana.model.BalanceSheetReport;
import com.testehan.finana.model.CashFlowData;
import com.testehan.finana.model.CashFlowReport;
import com.testehan.finana.model.CompanyEarningsTranscripts;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.EarningsEstimate;
import com.testehan.finana.model.EarningsHistory;
import com.testehan.finana.model.FinancialRatiosData;
import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.model.GlobalQuote;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.model.IncomeStatementData;
import com.testehan.finana.model.IndexData;
import com.testehan.finana.model.IndexQuotes;
import com.testehan.finana.model.QuarterlyEarningsTranscript;
import com.testehan.finana.model.RevenueGeographicSegmentationData;
import com.testehan.finana.model.RevenueSegmentationData;
import com.testehan.finana.model.StockQuotes;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.CashFlowRepository;
import com.testehan.finana.repository.CompanyEarningsTranscriptsRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.EarningsEstimatesRepository;
import com.testehan.finana.repository.EarningsHistoryRepository;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.repository.IndexQuotesRepository;
import com.testehan.finana.repository.RevenueGeographicSegmentationRepository;
import com.testehan.finana.repository.RevenueSegmentationDataRepository;
import com.testehan.finana.repository.StockQuotesRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            if (stockQuotesFromDb.isPresent() && isRecent(stockQuotesFromDb.get().getLastUpdated(), 100)) {
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
}
