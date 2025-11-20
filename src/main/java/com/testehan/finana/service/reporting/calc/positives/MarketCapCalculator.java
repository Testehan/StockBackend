package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MarketCapCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketCapCalculator.class);
    private final CompanyOverviewRepository companyOverviewRepository;

    public MarketCapCalculator(CompanyOverviewRepository companyOverviewRepository) {
        this.companyOverviewRepository = companyOverviewRepository;
    }

    private String formatMarketCap(double marketCap) {
        if (marketCap >= 1_000_000_000) {
            return String.format("%.1fB", marketCap / 1_000_000_000);
        } else if (marketCap >= 1_000_000) {
            return String.format("%.1fM", marketCap / 1_000_000);
        } else {
            return String.format("%.0f", marketCap); // For smaller values, just display the number
        }
    }

    public ReportItem calculate(String ticker) {
        Optional<CompanyOverview> companyOverviewOpt = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverviewOpt.isEmpty()) {
            LOGGER.warn("No company overview found for ticker: {}", ticker);
            return new ReportItem("marketCapSize", -1, "Could not retrieve company overview.");
        }

        CompanyOverview companyOverview = companyOverviewOpt.get();
        var marketCap = Double.valueOf(companyOverview.getMarketCap());
        String formattedMarketCap = formatMarketCap(marketCap);

        int score;
        String explanation;

        if (marketCap < 2_000_000_000L) {
            score = 5;
            explanation = "Market cap (" + formattedMarketCap +") is less than $2B, which is ideal for a potential 100-bagger.";
        } else if (marketCap < 5_000_000_000L) {
            score = 3;
            explanation = "Market cap (" + formattedMarketCap +") is less than $5B, which is a good starting point for high growth.";
        } else {
            score = 0;
            explanation ="Market cap (" + formattedMarketCap +") is greater than $5B, which may limit exponential growth potential.";
        }

        return new ReportItem("marketCapSize", score, explanation);
    }
}
