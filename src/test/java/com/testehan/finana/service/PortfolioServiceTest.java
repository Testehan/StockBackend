package com.testehan.finana.service;

import com.testehan.finana.model.user.UserPortfolio;
import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.repository.UserPortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private UserPortfolioRepository portfolioRepository;

    @Mock
    private QuoteService quoteService;

    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(portfolioRepository, quoteService);
    }

    @Test
    void getPortfolioAllocation_SingleStock_BuildsCorrectAllocation() {
        UserPortfolio portfolio = new UserPortfolio();
        portfolio.setUserId("user@test.com");
        portfolio.setStocks(List.of(stockHolding("AAPL", "150.00", "10")));

        GlobalQuote quote = new GlobalQuote();
        quote.setPrice("200.00");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));
        when(portfolioRepository.findByUserId("user@test.com")).thenReturn(Optional.of(portfolio));

        String allocation = portfolioService.getPortfolioAllocation("user@test.com");

        assertFalse(allocation.isEmpty());
        assertTrue(allocation.contains("AAPL"));
        assertTrue(allocation.contains("100.00%"));
    }

    @Test
    void getPortfolioAllocation_MultipleStocks_BuildsCorrectPercentages() {
        UserPortfolio portfolio = new UserPortfolio();
        portfolio.setUserId("user@test.com");

        List<UserPortfolio.StockHolding> stocks = new ArrayList<>();
        stocks.add(stockHolding("AAPL", "100.00", "10"));
        stocks.add(stockHolding("GOOGL", "50.00", "5"));
        portfolio.setStocks(stocks);

        GlobalQuote aaplQuote = new GlobalQuote();
        aaplQuote.setPrice("100.00");
        GlobalQuote googlQuote = new GlobalQuote();
        googlQuote.setPrice("50.00");

        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(aaplQuote));
        when(quoteService.getLastStockQuote("GOOGL")).thenReturn(Mono.just(googlQuote));
        when(portfolioRepository.findByUserId("user@test.com")).thenReturn(Optional.of(portfolio));

        String allocation = portfolioService.getPortfolioAllocation("user@test.com");

        assertTrue(allocation.contains("AAPL"));
        assertTrue(allocation.contains("GOOGL"));
        assertTrue(allocation.contains("80.00%"));
        assertTrue(allocation.contains("20.00%"));
    }

    @Test
    void getPortfolioAllocation_MixedStocksAndOtherAssets() {
        UserPortfolio portfolio = new UserPortfolio();
        portfolio.setUserId("user@test.com");
        portfolio.setStocks(List.of(stockHolding("AAPL", "100.00", "10")));

        UserPortfolio.AssetHolding cash = new UserPortfolio.AssetHolding();
        cash.setName("Savings");
        cash.setValue(new BigDecimal("1000.00"));
        portfolio.setOtherAssets(List.of(cash));

        GlobalQuote quote = new GlobalQuote();
        quote.setPrice("100.00");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));
        when(portfolioRepository.findByUserId("user@test.com")).thenReturn(Optional.of(portfolio));

        String allocation = portfolioService.getPortfolioAllocation("user@test.com");

        assertTrue(allocation.contains("AAPL"));
        assertTrue(allocation.contains("Savings"));
    }

    @Test
    void getPortfolioAllocation_EmptyPortfolio_ReturnsEmptyString() {
        UserPortfolio portfolio = new UserPortfolio();
        portfolio.setUserId("user@test.com");
        portfolio.setStocks(List.of());
        portfolio.setOtherAssets(List.of());

        when(portfolioRepository.findByUserId("user@test.com")).thenReturn(Optional.of(portfolio));

        String allocation = portfolioService.getPortfolioAllocation("user@test.com");

        assertEquals("", allocation);
    }

    @Test
    void getPortfolioAllocation_NonExistentUser_ReturnsEmptyString() {
        when(portfolioRepository.findByUserId("unknown@test.com")).thenReturn(Optional.empty());

        String allocation = portfolioService.getPortfolioAllocation("unknown@test.com");

        assertEquals("", allocation);
    }

    @Test
    void getPortfolioAllocation_QuoteServiceFails_UsesPurchasePriceFallback() {
        UserPortfolio portfolio = new UserPortfolio();
        portfolio.setUserId("user@test.com");
        portfolio.setStocks(List.of(stockHolding("AAPL", "95.00", "10")));

        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.error(new RuntimeException("API down")));
        when(portfolioRepository.findByUserId("user@test.com")).thenReturn(Optional.of(portfolio));

        String allocation = portfolioService.getPortfolioAllocation("user@test.com");

        assertTrue(allocation.contains("AAPL"));
        assertTrue(allocation.contains("100.00%"));
    }

    @Test
    void getPortfolioAllocation_QuotePriceBlank_UsesPurchasePriceFallback() {
        UserPortfolio portfolio = new UserPortfolio();
        portfolio.setUserId("user@test.com");
        portfolio.setStocks(List.of(stockHolding("AAPL", "80.00", "10")));

        GlobalQuote quote = new GlobalQuote();
        quote.setPrice("");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));
        when(portfolioRepository.findByUserId("user@test.com")).thenReturn(Optional.of(portfolio));

        String allocation = portfolioService.getPortfolioAllocation("user@test.com");

        assertTrue(allocation.contains("AAPL"));
        assertTrue(allocation.contains("100.00%"));
    }

    @Test
    void getPortfolioAllocation_QuotePriceNull_UsesPurchasePriceFallback() {
        UserPortfolio portfolio = new UserPortfolio();
        portfolio.setUserId("user@test.com");
        portfolio.setStocks(List.of(stockHolding("AAPL", "80.00", "10")));

        GlobalQuote quote = new GlobalQuote();
        quote.setPrice(null);
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));
        when(portfolioRepository.findByUserId("user@test.com")).thenReturn(Optional.of(portfolio));

        String allocation = portfolioService.getPortfolioAllocation("user@test.com");

        assertTrue(allocation.contains("AAPL"));
        assertTrue(allocation.contains("100.00%"));
    }

    @Test
    void getPriceOrFallback_LivePriceAvailable_ReturnsLivePrice() {
        GlobalQuote quote = new GlobalQuote();
        quote.setPrice("175.50");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));

        BigDecimal price = portfolioService.getPriceOrFallback("AAPL", new BigDecimal("150.00"));

        assertEquals(new BigDecimal("175.50"), price);
    }

    @Test
    void getPriceOrFallback_QuoteServiceFails_ReturnsFallback() {
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.error(new RuntimeException("API down")));

        BigDecimal price = portfolioService.getPriceOrFallback("AAPL", new BigDecimal("150.00"));

        assertEquals(new BigDecimal("150.00"), price);
    }

    @Test
    void getPriceOrFallback_EmptyPrice_ReturnsFallback() {
        GlobalQuote quote = new GlobalQuote();
        quote.setPrice("");
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));

        BigDecimal price = portfolioService.getPriceOrFallback("AAPL", new BigDecimal("150.00"));

        assertEquals(new BigDecimal("150.00"), price);
    }

    @Test
    void getPriceOrFallback_NullPrice_ReturnsFallback() {
        GlobalQuote quote = new GlobalQuote();
        quote.setPrice(null);
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.just(quote));

        BigDecimal price = portfolioService.getPriceOrFallback("AAPL", new BigDecimal("150.00"));

        assertEquals(new BigDecimal("150.00"), price);
    }

    @Test
    void getPriceOrFallback_QuoteServiceReturnsEmptyMono_ReturnsFallback() {
        when(quoteService.getLastStockQuote("AAPL")).thenReturn(Mono.empty());

        BigDecimal price = portfolioService.getPriceOrFallback("AAPL", new BigDecimal("150.00"));

        assertEquals(new BigDecimal("150.00"), price);
    }

    private UserPortfolio.StockHolding stockHolding(String symbol, String price, String shares) {
        UserPortfolio.StockHolding holding = new UserPortfolio.StockHolding();
        holding.setSymbol(symbol);
        holding.setPurchasePricePerStock(new BigDecimal(price));
        holding.setShares(new BigDecimal(shares));
        return holding;
    }
}