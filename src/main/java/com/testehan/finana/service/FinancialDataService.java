package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class FinancialDataService {

    private final AlphaVantageService alphaVantageService;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final SharesOutstandingRepository sharesOutstandingRepository;
    private final EarningsHistoryRepository earningsHistoryRepository;
    private final StockQuotesRepository stockQuotesRepository;
    private final SecApiService secApiService;



    private final CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository;


    public FinancialDataService(AlphaVantageService alphaVantageService, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository, CashFlowRepository cashFlowRepository, SharesOutstandingRepository sharesOutstandingRepository, CompanyOverviewRepository companyOverviewRepository, EarningsHistoryRepository earningsHistoryRepository, StockQuotesRepository stockQuotesRepository, CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository, SecApiService secApiService) {
        this.alphaVantageService = alphaVantageService;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.sharesOutstandingRepository = sharesOutstandingRepository;
        this.companyOverviewRepository = companyOverviewRepository;
        this.earningsHistoryRepository = earningsHistoryRepository;
        this.stockQuotesRepository = stockQuotesRepository;
        this.companyEarningsTranscriptsRepository = companyEarningsTranscriptsRepository;
        this.secApiService = secApiService;
    }

    public void ensureFinancialDataIsPresent(String ticker) {
        getGlobalQuote(ticker).block();
        getIncomeStatements(ticker).block();
        getBalanceSheet(ticker).block();
        getCashFlow(ticker).block();
        CompanyOverview companyOverview = getCompanyOverview(ticker).block();

        if (companyOverview != null && companyOverview.getLatestQuarter() != null) {
            getEarningsCallTranscript(ticker, companyOverview.getLatestQuarter()).block();
        }

        secApiService.getSectionFrom10K(ticker, "risk_factors").block();
        secApiService.getSectionFrom10K(ticker, "management_discussion").block();
        secApiService.getSectionFrom10K(ticker, "business_description").block();
        secApiService.getSectionFrom10Q(ticker, "risk_factors").block();
        secApiService.getSectionFrom10Q(ticker, "management_discussion").block();
    }

    public Mono<QuarterlyEarningsTranscript> getEarningsCallTranscript(String symbol, String quarter) {
        return Mono.defer(() -> {
            Optional<CompanyEarningsTranscripts> earningsCallTranscriptFromDb = companyEarningsTranscriptsRepository.findById(symbol.toUpperCase());
            if (earningsCallTranscriptFromDb.isPresent()) {
                Optional<QuarterlyEarningsTranscript> quarterlyTranscript = earningsCallTranscriptFromDb.get().getTranscripts().stream()
                        .filter(transcript -> transcript.getQuarter().equals(quarter))
                        .findFirst();

                if (quarterlyTranscript.isPresent()) {
                    return Mono.just(quarterlyTranscript.get());
                }
            }

            return alphaVantageService.fetchEarningsCallTranscriptFromApiAndSave(symbol.toUpperCase(), quarter)
                    .flatMap(companyTranscripts -> companyTranscripts.getTranscripts().stream()
                            .filter(transcript -> transcript.getQuarter().equals(quarter))
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

    public Mono<StockQuotes> getGlobalQuote(String symbol) {
        return Mono.defer(() -> {
            Optional<StockQuotes> stockQuotesFromDb = stockQuotesRepository.findBySymbol(symbol.toUpperCase());
            if (stockQuotesFromDb.isPresent() && isRecent(stockQuotesFromDb.get().getLastUpdated(), 10)) {
                return Mono.just(stockQuotesFromDb.get());
            } else {
                return alphaVantageService.fetchGlobalQuoteFromApi(symbol.toUpperCase())
                        .flatMap(globalQuote -> {
                            StockQuotes stockQuotes;
                            if (stockQuotesFromDb.isPresent()) {
                                stockQuotes = stockQuotesFromDb.get();
                                stockQuotes.getQuotes().add(globalQuote);
                            } else {
                                stockQuotes = new StockQuotes();
                                stockQuotes.setSymbol(symbol.toUpperCase());
                                stockQuotes.setQuotes(new java.util.ArrayList<>());
                                stockQuotes.getQuotes().add(globalQuote);
                            }
                            stockQuotes.setLastUpdated(LocalDateTime.now());
                            return Mono.just(stockQuotesRepository.save(stockQuotes));
                        });
            }
        });
    }

    public Mono<CompanyOverview> getCompanyOverview(String symbol) {
        return Mono.defer(() -> {
            Optional<CompanyOverview> overviewFromDb = companyOverviewRepository.findBySymbol(symbol.toUpperCase());
            if (overviewFromDb.isPresent() && isRecent(overviewFromDb.get().getLastUpdated(), 10080)) {
                return Mono.just(overviewFromDb.get());
            } else {
                return alphaVantageService.fetchCompanyOverviewFromApiAndSave(symbol.toUpperCase(), overviewFromDb)
                        .flatMap(overview -> Mono.just(companyOverviewRepository.save(overview)));
            }
        });
    }

    public Mono<IncomeStatementData> getIncomeStatements(String symbol) {
        return Mono.defer(() -> {
            Optional<IncomeStatementData> incomeStatementsFromDb = incomeStatementRepository.findBySymbol(symbol.toUpperCase());
            if (incomeStatementsFromDb.isPresent()) {
                return Mono.just(incomeStatementsFromDb.get());
            } else {
                return alphaVantageService.fetchIncomeStatementsFromApiAndSave(symbol.toUpperCase())
                        .flatMap(incomeStatementData -> Mono.just(incomeStatementRepository.save(incomeStatementData)));
            }
        });
    }

    public Mono<BalanceSheetData> getBalanceSheet(String symbol) {
        return Mono.defer(() -> {
            Optional<BalanceSheetData> balanceSheetFromDb = balanceSheetRepository.findBySymbol(symbol.toUpperCase());
            if (balanceSheetFromDb.isPresent()) {
                return Mono.just(balanceSheetFromDb.get());
            } else {
                return alphaVantageService.fetchBalanceSheetFromApiAndSave(symbol.toUpperCase())
                        .flatMap(balanceSheetData -> Mono.just(balanceSheetRepository.save(balanceSheetData)));
            }
        });
    }

    public Mono<CashFlowData> getCashFlow(String symbol) {
        return Mono.defer(() -> {
            Optional<CashFlowData> cashFlowFromDb = cashFlowRepository.findBySymbol(symbol.toUpperCase());
            if (cashFlowFromDb.isPresent()) {
                return Mono.just(cashFlowFromDb.get());
            } else {
                return alphaVantageService.fetchCashFlowFromApiAndSave(symbol.toUpperCase())
                        .flatMap(cashFlowData -> Mono.just(cashFlowRepository.save(cashFlowData)));
            }
        });
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
}
