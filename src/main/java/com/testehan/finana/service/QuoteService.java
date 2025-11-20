package com.testehan.finana.service;

import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.quote.IndexData;
import com.testehan.finana.model.quote.IndexQuotes;
import com.testehan.finana.model.quote.StockQuotes;
import com.testehan.finana.repository.IndexQuotesRepository;
import com.testehan.finana.repository.StockQuotesRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class QuoteService {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FMPService fmpService;
    private final StockQuotesRepository stockQuotesRepository;
    private final IndexQuotesRepository indexQuotesRepository;

    public QuoteService(FMPService fmpService, StockQuotesRepository stockQuotesRepository, IndexQuotesRepository indexQuotesRepository) {
        this.fmpService = fmpService;
        this.stockQuotesRepository = stockQuotesRepository;
        this.indexQuotesRepository = indexQuotesRepository;
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

    private boolean isRecent(LocalDateTime lastUpdated, int minutes) {
        if (lastUpdated == null) {
            return false;
        }
        return ChronoUnit.MINUTES.between(lastUpdated, LocalDateTime.now()) < minutes;
    }

    public void deleteBySymbol(String symbol) {
        stockQuotesRepository.deleteBySymbol(symbol);
    }

    public boolean hasStockQuotes(String symbol) {
        return stockQuotesRepository.findBySymbol(symbol).isPresent();
    }
}
