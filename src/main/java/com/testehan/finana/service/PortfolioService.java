package com.testehan.finana.service;

import com.testehan.finana.model.user.UserPortfolio;
import com.testehan.finana.repository.UserPortfolioRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PortfolioService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final UserPortfolioRepository portfolioRepository;
    private final QuoteService quoteService;

    public PortfolioService(UserPortfolioRepository portfolioRepository, QuoteService quoteService) {
        this.portfolioRepository = portfolioRepository;
        this.quoteService = quoteService;
    }

    public String getPortfolioAllocation(String userEmail) {
        return portfolioRepository.findByUserId(userEmail)
                .map(portfolio -> {
                    BigDecimal totalValue = calculateTotalValue(portfolio);

                    if (totalValue.compareTo(ZERO) == 0) {
                        return "";
                    }

                    return buildAllocationString(portfolio, totalValue);
                })
                .orElse("");
    }

    private BigDecimal calculateTotalValue(UserPortfolio portfolio) {
        BigDecimal totalValue = ZERO;

        if (portfolio.getStocks() != null) {
            for (UserPortfolio.StockHolding stock : portfolio.getStocks()) {
                BigDecimal price = getPriceOrFallback(stock.getSymbol(), stock.getPurchasePricePerStock());
                totalValue = totalValue.add(price.multiply(stock.getShares()));
            }
        }

        if (portfolio.getOtherAssets() != null) {
            for (UserPortfolio.AssetHolding asset : portfolio.getOtherAssets()) {
                totalValue = totalValue.add(asset.getValue());
            }
        }

        return totalValue;
    }

    private String buildAllocationString(UserPortfolio portfolio, BigDecimal totalValue) {
        StringBuilder sb = new StringBuilder();

        if (portfolio.getStocks() != null) {
            for (UserPortfolio.StockHolding stock : portfolio.getStocks()) {
                BigDecimal price = getPriceOrFallback(stock.getSymbol(), stock.getPurchasePricePerStock());
                BigDecimal positionValue = price.multiply(stock.getShares());
                BigDecimal percentage = positionValue.divide(totalValue, 4, RoundingMode.HALF_UP)
                        .multiply(ONE_HUNDRED)
                        .setScale(2, RoundingMode.HALF_UP);
                sb.append(String.format("- %s: %s%%\n", stock.getSymbol(), percentage));
            }
        }

        if (portfolio.getOtherAssets() != null) {
            for (UserPortfolio.AssetHolding asset : portfolio.getOtherAssets()) {
                BigDecimal percentage = asset.getValue().divide(totalValue, 4, RoundingMode.HALF_UP)
                        .multiply(ONE_HUNDRED)
                        .setScale(2, RoundingMode.HALF_UP);
                sb.append(String.format("- %s: %s%%\n", asset.getName(), percentage));
            }
        }

        return sb.toString();
    }

    public BigDecimal getPriceOrFallback(String symbol, BigDecimal fallback) {
        return quoteService.getLastStockQuote(symbol)
                .onErrorResume(e -> Mono.empty())
                .blockOptional()
                .filter(q -> q.getPrice() != null && !q.getPrice().isBlank())
                .map(q -> new BigDecimal(q.getPrice()))
                .orElse(fallback);
    }
}