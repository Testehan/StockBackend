package com.testehan.finana.service;

import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.quote.IndexData;
import com.testehan.finana.model.quote.IndexQuotes;
import com.testehan.finana.model.quote.StockQuotes;
import com.testehan.finana.repository.IndexQuotesRepository;
import com.testehan.finana.repository.StockQuotesRepository;
import com.testehan.finana.util.DateUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class QuoteService {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FMPService fmpService;
    private final StockQuotesRepository stockQuotesRepository;
    private final IndexQuotesRepository indexQuotesRepository;
    private final DateUtils dateUtils;

    public QuoteService(FMPService fmpService, StockQuotesRepository stockQuotesRepository, IndexQuotesRepository indexQuotesRepository, DateUtils dateUtils) {
        this.fmpService = fmpService;
        this.stockQuotesRepository = stockQuotesRepository;
        this.indexQuotesRepository = indexQuotesRepository;
        this.dateUtils = dateUtils;
    }

    public Mono<GlobalQuote> getLastStockQuote(String symbol) {
        return Mono.defer(() -> {
            Optional<StockQuotes> stockQuotesFromDb = stockQuotesRepository.findBySymbol(symbol);
            if (stockQuotesFromDb.isPresent() && !stockQuotesFromDb.get().getQuotes().isEmpty() && dateUtils.isRecent(stockQuotesFromDb.get().getLastUpdated(), DateUtils.CACHE_TEN_MINUTES)) {
                return Mono.fromCallable(() -> stockQuotesRepository.findLastQuoteBySymbol(symbol))
                        .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                        .switchIfEmpty(Mono.error(() -> new RuntimeException("No stock quote found for " + symbol)));
            } else {
                return fmpService.getHistoricalDividendAdjustedEodPrice(symbol)
                        .flatMap(globalQuotes -> Mono.fromCallable(() -> {
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
                            return stockQuotesRepository.save(stockQuotes);
                        }))
                        .flatMap(saved -> Mono.fromCallable(() -> stockQuotesRepository.findLastQuoteBySymbol(symbol))
                                .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                                .switchIfEmpty(Mono.error(() -> new RuntimeException("No stock quote found for " + symbol))));
            }
        });
    }

    public Mono<IndexQuotes> getIndexQuotes(String symbol) {
        return Mono.defer(() -> {
            Optional<IndexQuotes> indexQuotesFromDb = indexQuotesRepository.findById(symbol.toUpperCase());
            if (indexQuotesFromDb.isPresent() && dateUtils.isRecent(indexQuotesFromDb.get().getLastUpdated(), DateUtils.CACHE_HOUR_AND_A_HALF)) {
                return Mono.just(indexQuotesFromDb.get());
            } else {
                return fmpService.getIndexHistoricalData(symbol)
                        .flatMap(indexDataList -> Mono.fromCallable(() -> {
                            IndexQuotes indexQuotes;
                            if (indexQuotesFromDb.isPresent()) {
                                indexQuotes = indexQuotesFromDb.get();
                                indexQuotes.setQuotes(indexDataList);
                            } else {
                                indexQuotes = new IndexQuotes(symbol.toUpperCase(), indexDataList);
                            }
                            indexQuotes.setLastUpdated(LocalDateTime.now());
                            return indexQuotesRepository.save(indexQuotes);
                        }));
            }
        }).switchIfEmpty(Mono.error(() -> new RuntimeException("No index quotes found for " + symbol)));
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

    public void deleteBySymbol(String symbol) {
        stockQuotesRepository.deleteBySymbol(symbol);
    }

    public boolean hasStockQuotes(String symbol) {
        return stockQuotesRepository.findBySymbol(symbol).isPresent();
    }
}
