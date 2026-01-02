package com.testehan.finana.service;

import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.quote.IndexData;
import com.testehan.finana.model.quote.IndexQuotes;
import com.testehan.finana.model.quote.StockQuotes;
import com.testehan.finana.repository.IndexQuotesRepository;
import com.testehan.finana.repository.StockQuotesRepository;
import com.testehan.finana.util.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private FMPService fmpService;

    @Mock
    private StockQuotesRepository stockQuotesRepository;

    @Mock
    private IndexQuotesRepository indexQuotesRepository;

    @Mock
    private DateUtils dateUtils;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(fmpService, stockQuotesRepository, indexQuotesRepository, dateUtils);
    }

    @Test
    void getLastStockQuote_CacheHit_ReturnsCachedQuote() {
        String symbol = "AAPL";
        GlobalQuote quote = new GlobalQuote();
        quote.setSymbol(symbol);
        quote.setPrice("150.00");

        StockQuotes stockQuotes = new StockQuotes();
        stockQuotes.setSymbol(symbol);
        stockQuotes.setQuotes(List.of(quote));
        stockQuotes.setLastUpdated(LocalDateTime.now());

        when(stockQuotesRepository.findBySymbol(symbol)).thenReturn(Optional.of(stockQuotes));
        when(dateUtils.isRecent(any(), anyInt())).thenReturn(true);
        when(stockQuotesRepository.findLastQuoteBySymbol(symbol)).thenReturn(Optional.of(quote));

        GlobalQuote result = quoteService.getLastStockQuote(symbol).block();

        assertNotNull(result);
        assertEquals("150.00", result.getPrice());
        verify(fmpService, never()).getHistoricalDividendAdjustedEodPrice(anyString());
    }

    @Test
    void getLastStockQuote_CacheMiss_FetchesFromApi() {
        String symbol = "AAPL";
        GlobalQuote quote = new GlobalQuote();
        quote.setSymbol(symbol);
        quote.setPrice("155.00");

        when(stockQuotesRepository.findBySymbol(symbol)).thenReturn(Optional.empty());
        lenient().when(dateUtils.isRecent(any(), anyInt())).thenReturn(false);
        when(fmpService.getHistoricalDividendAdjustedEodPrice(symbol)).thenReturn(Mono.just(List.of(quote)));
        when(stockQuotesRepository.save(any(StockQuotes.class))).thenAnswer(i -> i.getArguments()[0]);
        when(stockQuotesRepository.findLastQuoteBySymbol(symbol)).thenReturn(Optional.of(quote));

        GlobalQuote result = quoteService.getLastStockQuote(symbol).block();

        assertNotNull(result);
        assertEquals("155.00", result.getPrice());
    }

    @Test
    void getLastStockQuote_ApiEmpty_ReturnsCachedIfAvailable() {
        String symbol = "AAPL";
        GlobalQuote oldQuote = new GlobalQuote();
        oldQuote.setSymbol(symbol);
        oldQuote.setPrice("145.00");

        StockQuotes stockQuotes = new StockQuotes();
        stockQuotes.setSymbol(symbol);
        stockQuotes.setQuotes(List.of(oldQuote));
        stockQuotes.setLastUpdated(LocalDateTime.now().minusHours(1));

        when(stockQuotesRepository.findBySymbol(symbol)).thenReturn(Optional.of(stockQuotes));
        when(dateUtils.isRecent(any(), anyInt())).thenReturn(false);
        when(fmpService.getHistoricalDividendAdjustedEodPrice(symbol)).thenReturn(Mono.just(List.of()));
        when(stockQuotesRepository.findLastQuoteBySymbol(symbol)).thenReturn(Optional.of(oldQuote));

        GlobalQuote result = quoteService.getLastStockQuote(symbol).block();

        assertNotNull(result);
        assertEquals("145.00", result.getPrice());
    }

    @Test
    void getLastStockQuote_ApiFails_ReturnsCachedIfAvailable() {
        String symbol = "AAPL";
        GlobalQuote oldQuote = new GlobalQuote();
        oldQuote.setSymbol(symbol);
        oldQuote.setPrice("145.00");

        StockQuotes stockQuotes = new StockQuotes();
        stockQuotes.setSymbol(symbol);
        stockQuotes.setQuotes(List.of(oldQuote));
        stockQuotes.setLastUpdated(LocalDateTime.now().minusHours(1));

        when(stockQuotesRepository.findBySymbol(symbol)).thenReturn(Optional.of(stockQuotes));
        when(dateUtils.isRecent(any(), anyInt())).thenReturn(false);
        when(fmpService.getHistoricalDividendAdjustedEodPrice(symbol))
                .thenReturn(Mono.error(new RuntimeException("API down")));
        when(stockQuotesRepository.findLastQuoteBySymbol(symbol)).thenReturn(Optional.of(oldQuote));

        GlobalQuote result = quoteService.getLastStockQuote(symbol).block();

        assertNotNull(result);
        assertEquals("145.00", result.getPrice());
    }

    @Test
    void getLastStockQuote_ApiFailsNoCache_ThrowsException() {
        String symbol = "AAPL";

        when(stockQuotesRepository.findBySymbol(symbol)).thenReturn(Optional.empty());
        lenient().when(dateUtils.isRecent(any(), anyInt())).thenReturn(false);
        when(fmpService.getHistoricalDividendAdjustedEodPrice(symbol))
                .thenReturn(Mono.error(new RuntimeException("API down")));

        assertThrows(RuntimeException.class, () -> quoteService.getLastStockQuote(symbol).block());
    }

    @Test
    void getIndexQuotes_CacheHit_ReturnsCached() {
        String symbol = "^GSPC";
        IndexData data = new IndexData();
        data.setSymbol(symbol);

        IndexQuotes indexQuotes = new IndexQuotes(symbol, List.of(data));
        indexQuotes.setLastUpdated(LocalDateTime.now());

        when(indexQuotesRepository.findById(symbol)).thenReturn(Optional.of(indexQuotes));
        when(dateUtils.isRecent(any(), anyInt())).thenReturn(true);

        IndexQuotes result = quoteService.getIndexQuotes(symbol).block();

        assertNotNull(result);
        verify(fmpService, never()).getIndexHistoricalData(anyString());
    }

    @Test
    void getIndexQuotes_CacheMiss_FetchesFromApi() {
        String symbol = "^GSPC";
        IndexData data = new IndexData();
        data.setSymbol(symbol);

        when(indexQuotesRepository.findById(symbol)).thenReturn(Optional.empty());
        lenient().when(dateUtils.isRecent(any(), anyInt())).thenReturn(false);
        when(fmpService.getIndexHistoricalData(symbol)).thenReturn(Mono.just(List.of(data)));
        when(indexQuotesRepository.save(any(IndexQuotes.class))).thenAnswer(i -> i.getArguments()[0]);

        IndexQuotes result = quoteService.getIndexQuotes(symbol).block();

        assertNotNull(result);
    }

    @Test
    void getStockQuoteByDate_FindsQuoteOnExactDate() {
        String symbol = "AAPL";
        LocalDate date = LocalDate.of(2024, 6, 15);

        GlobalQuote quote = new GlobalQuote();
        quote.setSymbol(symbol);
        quote.setDate("2024-06-15");

        when(stockQuotesRepository.findQuoteBySymbolAndDate(symbol, "2024-06-15"))
                .thenReturn(Optional.of(quote));

        Optional<GlobalQuote> result = quoteService.getStockQuoteByDate(symbol, date);

        assertTrue(result.isPresent());
    }

    @Test
    void getStockQuoteByDate_FallsBackToPreviousDays() {
        String symbol = "AAPL";
        LocalDate date = LocalDate.of(2024, 6, 15);

        GlobalQuote quote = new GlobalQuote();
        quote.setSymbol(symbol);
        quote.setDate("2024-06-14");

        when(stockQuotesRepository.findQuoteBySymbolAndDate(symbol, "2024-06-15"))
                .thenReturn(Optional.empty());
        when(stockQuotesRepository.findQuoteBySymbolAndDate(symbol, "2024-06-14"))
                .thenReturn(Optional.of(quote));

        Optional<GlobalQuote> result = quoteService.getStockQuoteByDate(symbol, date);

        assertTrue(result.isPresent());
        assertEquals("2024-06-14", result.get().getDate());
    }

    @Test
    void getStockQuoteByDate_NotFoundIn7Days_ReturnsEmpty() {
        String symbol = "AAPL";
        LocalDate date = LocalDate.of(2024, 6, 15);

        when(stockQuotesRepository.findQuoteBySymbolAndDate(anyString(), anyString()))
                .thenReturn(Optional.empty());

        Optional<GlobalQuote> result = quoteService.getStockQuoteByDate(symbol, date);

        assertTrue(result.isEmpty());
    }

    @Test
    void getIndexQuoteByDate_FallsBackToPreviousDays() {
        String symbol = "^GSPC";
        LocalDate date = LocalDate.of(2024, 6, 15);

        IndexData indexData = new IndexData();
        indexData.setDate("2024-06-14");

        when(indexQuotesRepository.findQuoteBySymbolAndDate(symbol, "2024-06-15"))
                .thenReturn(Optional.empty());
        when(indexQuotesRepository.findQuoteBySymbolAndDate(symbol, "2024-06-14"))
                .thenReturn(Optional.of(indexData));

        Optional<IndexData> result = quoteService.getIndexQuoteByDate(symbol, date);

        assertTrue(result.isPresent());
    }

    @Test
    void hasStockQuotes_Exists_ReturnsTrue() {
        String symbol = "AAPL";
        when(stockQuotesRepository.findBySymbol(symbol)).thenReturn(Optional.of(new StockQuotes()));

        boolean result = quoteService.hasStockQuotes(symbol);

        assertTrue(result);
    }

    @Test
    void hasStockQuotes_NotExists_ReturnsFalse() {
        String symbol = "AAPL";
        when(stockQuotesRepository.findBySymbol(symbol)).thenReturn(Optional.empty());

        boolean result = quoteService.hasStockQuotes(symbol);

        assertFalse(result);
    }

    @Test
    void deleteBySymbol_CallsRepository() {
        String symbol = "AAPL";
        quoteService.deleteBySymbol(symbol);
        verify(stockQuotesRepository).deleteBySymbol(symbol);
    }
}