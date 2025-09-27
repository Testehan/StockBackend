package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.*;
import com.testehan.finana.util.DateUtils;
import com.testehan.finana.util.FinancialRatiosCalculator;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final SharesOutstandingRepository sharesOutstandingRepository;
    private final EarningsHistoryRepository earningsHistoryRepository;
    private final StockQuotesRepository stockQuotesRepository;
    private final EarningsEstimatesRepository earningsEstimatesRepository;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final GeneratedReportRepository generatedReportRepository;
    private final SecFilingRepository secFilingRepository;
    private final RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    private final RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository;
    private final IndexQuotesRepository indexQuotesRepository;
    private final SecFilingsRepository secFilingsRepository;

    private final FinancialRatiosCalculator financialRatiosCalculator;

    private final DateUtils dateUtils;

    private final CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository;


    public FinancialDataService(AlphaVantageService alphaVantageService, FMPService fmpService, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository, CashFlowRepository cashFlowRepository, SharesOutstandingRepository sharesOutstandingRepository, CompanyOverviewRepository companyOverviewRepository, EarningsHistoryRepository earningsHistoryRepository, StockQuotesRepository stockQuotesRepository, CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository, DateUtils dateUtils, EarningsEstimatesRepository earningsEstimatesRepository, FinancialRatiosRepository financialRatiosRepository, GeneratedReportRepository generatedReportRepository, SecFilingRepository secFilingRepository, RevenueSegmentationDataRepository revenueSegmentationDataRepository, RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository, IndexQuotesRepository indexQuotesRepository, FinancialRatiosCalculator financialRatiosCalculator, SecFilingsRepository secFilingsRepository) {
        this.alphaVantageService = alphaVantageService;
        this.fmpService = fmpService;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.sharesOutstandingRepository = sharesOutstandingRepository;
        this.companyOverviewRepository = companyOverviewRepository;
        this.earningsHistoryRepository = earningsHistoryRepository;
        this.stockQuotesRepository = stockQuotesRepository;
        this.companyEarningsTranscriptsRepository = companyEarningsTranscriptsRepository;
        this.dateUtils = dateUtils;
        this.earningsEstimatesRepository = earningsEstimatesRepository;
        this.financialRatiosRepository = financialRatiosRepository;
        this.generatedReportRepository = generatedReportRepository;
        this.secFilingRepository = secFilingRepository;
        this.revenueSegmentationDataRepository = revenueSegmentationDataRepository;
        this.revenueGeographicSegmentationRepository = revenueGeographicSegmentationRepository;
        this.indexQuotesRepository = indexQuotesRepository;
        this.financialRatiosCalculator = financialRatiosCalculator;
        this.secFilingsRepository = secFilingsRepository;
    }

    public void ensureFinancialDataIsPresent(String ticker) {
        getLastStockQuote(ticker).block();
        getIndexQuotes("^GSPC").block();
        getIncomeStatements(ticker).block();
        getBalanceSheet(ticker).block();
        getCashFlow(ticker).block();
        getEarningsEstimates(ticker).block();
        getEarningsHistory(ticker).block();
        getCompanyOverview(ticker).block().get(0);
        getRevenueSegmentation(ticker).block();
        getRevenueGeographicSegmentation(ticker).block();
        fetchAndSaveSecFilings(ticker);
        getAndSaveSecFilings(ticker);

        getFinancialRatios(ticker);

        var latestReportDate = getLatestReportedDate(ticker);

        if (latestReportDate != null) {
            String dateQuarter = dateUtils.getDateQuarter(latestReportDate);
            getEarningsCallTranscript(ticker, dateQuarter).block();
        }
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
                return alphaVantageService.fetchEarningsHistoryFromApiAndSave(symbol.toUpperCase())
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
                return alphaVantageService.fetchEarningsEstimatesFromApi(symbol.toUpperCase())
                        .flatMap(earningsEstimate -> {
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

    public void fetchAndSaveSecFilings(String symbol) {
        Optional<SecFilingsUrls> existingSecFilingsOptional = secFilingsRepository.findById(symbol);
        if (existingSecFilingsOptional.isPresent()) {
            SecFilingsUrls existingSecFilings = existingSecFilingsOptional.get();
            if (existingSecFilings.getLastUpdated() != null &&
                    ChronoUnit.DAYS.between(existingSecFilings.getLastUpdated(), LocalDateTime.now()) < 30) {
                LOGGER.info("SEC filings for symbol {} are recent. Skipping fetch.", symbol);
                return;
            }
        }

        List<SecFilingUrlData> secFilingData = fmpService.getSecFilings(symbol).block();
        if (secFilingData == null) {
            return;
        }

        SecFilingsUrls secFilingsUrls = existingSecFilingsOptional.orElse(new SecFilingsUrls(symbol, new ArrayList<>()));
        List<String> existingDates = secFilingsUrls.getFilings().stream().map(SecFilingUrlData::getFilingDate).toList();
        for (SecFilingUrlData newFiling : secFilingData) {
            if (!existingDates.contains(newFiling.getFilingDate())) {
                secFilingsUrls.getFilings().add(newFiling);
            }
        }
        secFilingsUrls.setLastUpdated(LocalDateTime.now());
        secFilingsRepository.save(secFilingsUrls);
    }

    public Mono<SharesOutstandingData> getSharesOutstanding(String symbol) {
        return Mono.defer(() -> {
            Optional<SharesOutstandingData> sharesOutstandingFromDb = sharesOutstandingRepository.findBySymbol(symbol.toUpperCase());
            if (sharesOutstandingFromDb.isPresent()) {
                return Mono.just(sharesOutstandingFromDb.get());
            } else {
                return alphaVantageService.fetchSharesOutstandingFromApiAndSave(symbol.toUpperCase())
                        .flatMap(sharesOutstandingData -> Mono.just(sharesOutstandingRepository.save(sharesOutstandingData)));
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
        sharesOutstandingRepository.deleteBySymbol(upperCaseSymbol);
        stockQuotesRepository.deleteBySymbol(upperCaseSymbol);
        earningsEstimatesRepository.deleteBySymbol(upperCaseSymbol);
        financialRatiosRepository.deleteBySymbol(upperCaseSymbol);
        generatedReportRepository.deleteBySymbol(upperCaseSymbol);
        secFilingRepository.deleteBySymbol(upperCaseSymbol);
        revenueGeographicSegmentationRepository.deleteBySymbol(upperCaseSymbol);
        secFilingsRepository.deleteById(upperCaseSymbol);

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
        Optional<SharesOutstandingData> sharesOutstandingDataOptional = getSharesOutstanding(symbol).blockOptional();

        if (incomeStatementDataOptional.isPresent() && balanceSheetDataOptional.isPresent()
                && cashFlowDataOptional.isPresent() && sharesOutstandingDataOptional.isPresent()) {
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

    private void getAndSaveSecFilings(String symbol) {
        Optional<SecFilingsUrls> secFilingsOptional = secFilingsRepository.findById(symbol);
        if (secFilingsOptional.isEmpty()) {
            LOGGER.warn("No SEC filings found for symbol: {}", symbol);
            return;
        }

        SecFilingsUrls secFilings = secFilingsOptional.get();
        Optional<SecFiling> existingSecFilingOptional = secFilingRepository.findById(symbol);

        // Get the latest 10-K filing
        Optional<SecFilingUrlData> latest10K = secFilings.getFilings().stream()
                .filter(filing -> "10-K".equals(filing.getFormType()))
                .max(Comparator.comparing(SecFilingUrlData::getFilingDate));

        latest10K.ifPresent(filing -> {
            boolean reprocess = true;
            if (existingSecFilingOptional.isPresent()) {
                Optional<TenKFilings> existing10K = existingSecFilingOptional.get().getTenKFilings().stream()
                        .filter(tenK -> tenK.getFiledAt().equals(filing.getFilingDate()))
                        .findFirst();
                if (existing10K.isPresent()) {
                    TenKFilings tenK = existing10K.get();
                    if (tenK.getBusinessDescription() != null && tenK.getRiskFactors() != null && tenK.getManagementDiscussion() != null) {
                        reprocess = false;
                    }
                }
            }
            if (reprocess) {
                getAndSaveSecFiling(symbol, filing.getFinalLink(), "10-K", filing.getFilingDate());
            }
        });

        // Get the latest 10-Q filing
        Optional<SecFilingUrlData> latest10Q = secFilings.getFilings().stream()
                .filter(filing -> "10-Q".equals(filing.getFormType()))
                .max(Comparator.comparing(SecFilingUrlData::getFilingDate));

        latest10Q.ifPresent(filing -> {
            boolean reprocess = true;
            if (existingSecFilingOptional.isPresent()) {
                Optional<TenQFilings> existing10Q = existingSecFilingOptional.get().getTenQFilings().stream()
                        .filter(tenQ -> tenQ.getFiledAt().equals(filing.getFilingDate()))
                        .findFirst();
                if (existing10Q.isPresent()) {
                    TenQFilings tenQ = existing10Q.get();
                    if (tenQ.getRiskFactors() != null && tenQ.getManagementDiscussion() != null) {
                        reprocess = false;
                    }
                }
            }
            if (reprocess) {
                getAndSaveSecFiling(symbol, filing.getFinalLink(), "10-Q", filing.getFilingDate());
            }
        });
    }

    private void getAndSaveSecFiling(String symbol, String finalLink, String formType, String filingDate) {
        try {
            Document doc = Jsoup.connect(finalLink)
                    .userAgent("CasaMia.ai admin@casamia.ai")
                    .maxBodySize(0)
                    .timeout(30 * 1000)
                    .get();
            String fullText = doc.text();

            if (fullText.isEmpty()) {
                LOGGER.error("Could not fetch filing text for URL: {}", finalLink);
                return;
            }

            LOGGER.info("Document fetched for symbol '{}'. Total length: {} characters.", symbol, fullText.length());

            String businessDescription = null;
            String riskFactors;
            String managementDiscussion;

            if ("10-K".equals(formType)) {
                businessDescription = extractSection(fullText, "Item\\s+1[:.]\\s+Business", "Item\\s+1A[:.]\\s+Risk",1000);
                riskFactors = extractSection(fullText, "Item\\s+1A[:.]\\s+Risk", "Item\\s+1B[:.]\\s+Unresolved|Item\\s+2[:.]\\s+Properties",1000);
                managementDiscussion = extractSection(fullText, "Item\\s+7[:.]\\s+Management", "Item\\s+7A[:.]\\s+Quantitative",1000);
            } else { // 10-Q
                riskFactors = extractSection(fullText, "Item\\s+1A[:.]\\s+Risk", "Item\\s+2[:.]\\s+Unregistered",150);
                managementDiscussion = extractSection(fullText, "Item\\s+2[:.]\\s+Management", "Item\\s+3[:.]\\s+Quantitative",500);
            }


            SecFiling secFiling = secFilingRepository.findById(symbol).orElse(new SecFiling());
            secFiling.setSymbol(symbol);

            if ("10-K".equals(formType)) {
                TenKFilings tenKFilings = new TenKFilings();
                tenKFilings.setBusinessDescription(businessDescription);
                tenKFilings.setRiskFactors(riskFactors);
                tenKFilings.setManagementDiscussion(managementDiscussion);
                tenKFilings.setFiledAt(filingDate);
                tenKFilings.setFilingUrl(finalLink);
                if (secFiling.getTenKFilings() == null) {
                    secFiling.setTenKFilings(new ArrayList<>());
                }
                secFiling.getTenKFilings().add(tenKFilings);
            } else if ("10-Q".equals(formType)) {
                TenQFilings tenQFilings = new TenQFilings();
                tenQFilings.setRiskFactors(riskFactors);
                tenQFilings.setManagementDiscussion(managementDiscussion);
                tenQFilings.setFiledAt(filingDate);
                tenQFilings.setFilingUrl(finalLink);
                if (secFiling.getTenQFilings() == null) {
                    secFiling.setTenQFilings(new ArrayList<>());
                }
                secFiling.getTenQFilings().add(tenQFilings);
            }

            secFilingRepository.save(secFiling);
            LOGGER.info("Successfully saved SEC filing for symbol: {}", symbol);

        } catch (Exception e) {
            LOGGER.error("Error processing SEC filing for symbol: " + symbol, e);
        }
    }

    private String extractSection(String fullText, String startRegex, String endRegex, int minimumNrOfCharsExpectedInSection) {
        // Compile patterns case-insensitive
        Pattern startPattern = Pattern.compile(startRegex, Pattern.CASE_INSENSITIVE);
        Pattern endPattern = Pattern.compile(endRegex, Pattern.CASE_INSENSITIVE);

        Matcher startMatcher = startPattern.matcher(fullText);

        while (startMatcher.find()) {
            int startIndex = startMatcher.start();

            // Search for the end pattern *after* the start found
            Matcher endMatcher = endPattern.matcher(fullText);

            // We only look for the end marker AFTER the start marker
            if (endMatcher.find(startMatcher.end())) {
                int endIndex = endMatcher.start();

                // Extract the content
                String candidate = fullText.substring(startIndex, endIndex);

                // --- HEURISTIC FOR TABLE OF CONTENTS ---
                // If the text between "Item 1" and "Item 1A" is very short (e.g., < 1000 chars),
                // it is likely a Table of Contents entry, not the actual section.
                // We skip it and look for the next match.
                if (candidate.length() > minimumNrOfCharsExpectedInSection) {
                    return candidate.trim();
                }
            }
        }
        return null; // Not found
    }
}
