package com.testehan.finana.controller;

import com.testehan.finana.model.user.UserPortfolio;
import com.testehan.finana.model.user.UserPortfolio.AssetHolding;
import com.testehan.finana.model.user.UserPortfolio.StockHolding;
import com.testehan.finana.repository.UserPortfolioRepository;
import com.testehan.finana.service.QuoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users/{userId}/portfolio")
public class PortfolioController {

    public static final String OTHER_ASSET = "OTHER_ASSET";
    private final UserPortfolioRepository portfolioRepository;
    private final QuoteService quoteService;

    public PortfolioController(UserPortfolioRepository portfolioRepository, QuoteService quoteService) {
        this.portfolioRepository = portfolioRepository;
        this.quoteService = quoteService;
    }

    @GetMapping
    public ResponseEntity<?> getPortfolio(@PathVariable String userId) {
        UserPortfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPortfolio newPortfolio = new UserPortfolio();
                    newPortfolio.setUserId(userId);
                    return newPortfolio;
                });

        List<Map<String, Object>> items = new ArrayList<>();

        // Add stocks
        if (portfolio.getStocks() != null) {
            for (StockHolding stock : portfolio.getStocks()) {
                Map<String, Object> item = new HashMap<>();
                item.put("symbol", stock.getSymbol().toUpperCase());
                item.put("shares", stock.getShares());
                item.put("purchasePricePerStock", stock.getPurchasePricePerStock());
                item.put("type", "stock");

                quoteService.getLastStockQuote(stock.getSymbol())
                        .onErrorResume(e -> Mono.empty())
                        .blockOptional()
                        .ifPresentOrElse(
                                quote -> item.put("currentPrice", quote.getPrice()),
                                () -> item.put("currentPrice", stock.getPurchasePricePerStock())
                        );

                items.add(item);
            }
        }

        // Add other assets
        if (portfolio.getOtherAssets() != null) {
            for (AssetHolding asset : portfolio.getOtherAssets()) {
                Map<String, Object> item = new HashMap<>();
                item.put("value", asset.getValue());
                item.put("name", asset.getName());
                item.put("type",  OTHER_ASSET);

                items.add(item);
            }
        }

        return ResponseEntity.ok(items);
    }

    @PostMapping
    public ResponseEntity<?> addItem(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        String symbol = ((String) body.get("symbol")).toUpperCase();
        Object shares = body.get("shares");
        Object value = body.get("value");
        String type = (String) body.getOrDefault("type", "stock");

        UserPortfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPortfolio newPortfolio = new UserPortfolio();
                    newPortfolio.setUserId(userId);
                    return newPortfolio;
                });

        if (OTHER_ASSET.equalsIgnoreCase(type)) {
            String name = (String) body.getOrDefault("name", symbol);
            BigDecimal assetValue = value != null ? new BigDecimal(value.toString()) : BigDecimal.ZERO;

            AssetHolding asset = new AssetHolding();
            asset.setName(name);
            asset.setValue(assetValue);

            if (portfolio.getOtherAssets() == null) {
                portfolio.setOtherAssets(new ArrayList<>());
            }
            portfolio.getOtherAssets().add(asset);
        } else {
            BigDecimal stockShares = shares != null ? new BigDecimal(shares.toString()) : BigDecimal.ZERO;
            BigDecimal stockValue = value != null ? new BigDecimal(value.toString()) : BigDecimal.ZERO;

            StockHolding stock = new StockHolding();
            stock.setSymbol(symbol.toUpperCase());
            stock.setShares(stockShares);
            stock.setPurchasePricePerStock(stockValue);

            if (portfolio.getStocks() == null) {
                portfolio.setStocks(new ArrayList<>());
            }
            portfolio.getStocks().add(stock);
        }

        portfolioRepository.save(portfolio);
        return ResponseEntity.ok("Item added to portfolio");
    }

    @PutMapping("/{symbol}")
    public ResponseEntity<?> updateItem(@PathVariable String userId, @PathVariable String symbol,
                                      @RequestBody Map<String, Object> body) {
        String symbolUpper = symbol.toUpperCase();
        Object shares = body.get("shares");
        Object value = body.get("value");

        UserPortfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Portfolio not found"));

        // First check stocks
        if (portfolio.getStocks() != null) {
            for (StockHolding stock : portfolio.getStocks()) {
                if (stock.getSymbol() != null && stock.getSymbol().equalsIgnoreCase(symbolUpper)) {
                    if (shares != null) {
                        stock.setShares(new BigDecimal(shares.toString()));
                    }
                    if (value != null) {
                        stock.setPurchasePricePerStock(new BigDecimal(value.toString()));
                    }
                    portfolioRepository.save(portfolio);
                    return ResponseEntity.ok("Stock updated");
                }
            }
        }

        // Check other assets
        if (portfolio.getOtherAssets() != null) {
            for (AssetHolding asset : portfolio.getOtherAssets()) {
                if (asset.getName() != null && asset.getName().equalsIgnoreCase(symbolUpper)) {
                    if (value != null) {
                        asset.setValue(new BigDecimal(value.toString()));
                    }
                    portfolioRepository.save(portfolio);
                    return ResponseEntity.ok("Asset updated");
                }
            }
        }

        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<?> deleteItem(@PathVariable String userId, @PathVariable String symbol) {
        String symbolUpper = symbol.toUpperCase();

        UserPortfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Portfolio not found"));

        boolean removed = false;

        // Check stocks first
        if (portfolio.getStocks() != null) {
            removed = portfolio.getStocks().removeIf(stock -> 
                stock.getSymbol() != null && stock.getSymbol().equalsIgnoreCase(symbolUpper));
        }

        // If not found in stocks, check other assets
        if (!removed && portfolio.getOtherAssets() != null) {
            removed = portfolio.getOtherAssets().removeIf(asset -> 
                asset.getName() != null && asset.getName().equalsIgnoreCase(symbolUpper));
        }

        if (removed) {
            portfolioRepository.save(portfolio);
            return ResponseEntity.ok("Item removed");
        }

        return ResponseEntity.notFound().build();
    }
}