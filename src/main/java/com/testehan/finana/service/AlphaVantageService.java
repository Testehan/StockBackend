package com.testehan.finana.service;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.repository.CompanyOverviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class AlphaVantageService {

    private final WebClient webClient;
    private final CompanyOverviewRepository repository;

    @Value("${alphavantage.api.key}")
    private String apiKey;

    @Autowired
    public AlphaVantageService(WebClient.Builder webClientBuilder, CompanyOverviewRepository repository) {
        this.webClient = webClientBuilder.baseUrl("https://www.alphavantage.co").build();
        this.repository = repository;
    }

    public Mono<CompanyOverview> getCompanyOverview(String symbol) {
        return Mono.defer(() -> {
            Optional<CompanyOverview> overviewFromDb = repository.findBySymbol(symbol.toUpperCase());
            if (overviewFromDb.isPresent() && isRecent(overviewFromDb.get().getLastUpdated())) {
                return Mono.just(overviewFromDb.get());
            } else {
                return fetchFromApiAndSave(symbol.toUpperCase(), overviewFromDb);
            }
        });
    }

    private boolean isRecent(LocalDateTime lastUpdated) {
        if (lastUpdated == null) {
            return false;
        }
        return ChronoUnit.WEEKS.between(lastUpdated, LocalDateTime.now()) < 1;
    }

    private Mono<CompanyOverview> fetchFromApiAndSave(String symbol, Optional<CompanyOverview> existingOverview) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "OVERVIEW")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(CompanyOverview.class)
                .flatMap(newOverview -> {
                    CompanyOverview overviewToSave = existingOverview.orElse(new CompanyOverview());
                    updateOverview(overviewToSave, newOverview);
                    overviewToSave.setLastUpdated(LocalDateTime.now());
                    return Mono.just(repository.save(overviewToSave));
                });
    }

    private void updateOverview(CompanyOverview existing, CompanyOverview newOverview) {
        existing.setSymbol(newOverview.getSymbol());
        existing.setAssetType(newOverview.getAssetType());
        existing.setName(newOverview.getName());
        existing.setDescription(newOverview.getDescription());
        existing.setCik(newOverview.getCik());
        existing.setExchange(newOverview.getExchange());
        existing.setCurrency(newOverview.getCurrency());
        existing.setCountry(newOverview.getCountry());
        existing.setSector(newOverview.getSector());
        existing.setIndustry(newOverview.getIndustry());
        existing.setAddress(newOverview.getAddress());
        existing.setFiscalYearEnd(newOverview.getFiscalYearEnd());
        existing.setLatestQuarter(newOverview.getLatestQuarter());
        existing.setMarketCapitalization(newOverview.getMarketCapitalization());
        existing.setEbitda(newOverview.getEbitda());
        existing.setPeRatio(newOverview.getPeRatio());
        existing.setPegRatio(newOverview.getPegRatio());
        existing.setBookValue(newOverview.getBookValue());
        existing.setDividendPerShare(newOverview.getDividendPerShare());
        existing.setDividendYield(newOverview.getDividendYield());
        existing.setEps(newOverview.getEps());
        existing.setRevenuePerShareTTM(newOverview.getRevenuePerShareTTM());
        existing.setProfitMargin(newOverview.getProfitMargin());
        existing.setOperatingMarginTTM(newOverview.getOperatingMarginTTM());
        existing.setReturnOnAssetsTTM(newOverview.getReturnOnAssetsTTM());
        existing.setReturnOnEquityTTM(newOverview.getReturnOnEquityTTM());
        existing.setRevenueTTM(newOverview.getRevenueTTM());
        existing.setGrossProfitTTM(newOverview.getGrossProfitTTM());
        existing.setDilutedEPSTTM(newOverview.getDilutedEPSTTM());
        existing.setQuarterlyEarningsGrowthYOY(newOverview.getQuarterlyEarningsGrowthYOY());
        existing.setQuarterlyRevenueGrowthYOY(newOverview.getQuarterlyRevenueGrowthYOY());
        existing.setAnalystTargetPrice(newOverview.getAnalystTargetPrice());
        existing.setTrailingPE(newOverview.getTrailingPE());
        existing.setForwardPE(newOverview.getForwardPE());
        existing.setPriceToSalesRatioTTM(newOverview.getPriceToSalesRatioTTM());
        existing.setPriceToBookRatio(newOverview.getPriceToBookRatio());
        existing.setEvToRevenue(newOverview.getEvToRevenue());
        existing.setEvToEBITDA(newOverview.getEvToEBITDA());
        existing.setBeta(newOverview.getBeta());
        existing.setFiftyTwoWeekHigh(newOverview.getFiftyTwoWeekHigh());
        existing.setFiftyTwoWeekLow(newOverview.getFiftyTwoWeekLow());
        existing.setFiftyDayMovingAverage(newOverview.getFiftyDayMovingAverage());
        existing.setTwoHundredDayMovingAverage(newOverview.getTwoHundredDayMovingAverage());
        existing.setSharesOutstanding(newOverview.getSharesOutstanding());
        existing.setDividendDate(newOverview.getDividendDate());
        existing.setExDividendDate(newOverview.getExDividendDate());
        existing.setOfficialSite(newOverview.getOfficialSite());
        existing.setAnalystRatingStrongBuy(newOverview.getAnalystRatingStrongBuy());
        existing.setAnalystRatingBuy(newOverview.getAnalystRatingBuy());
        existing.setAnalystRatingHold(newOverview.getAnalystRatingHold());
        existing.setAnalystRatingSell(newOverview.getAnalystRatingSell());
        existing.setAnalystRatingStrongSell(newOverview.getAnalystRatingStrongSell());
        existing.setSharesFloat(newOverview.getSharesFloat());
        existing.setPercentInsiders(newOverview.getPercentInsiders());
        existing.setPercentInstitutions(newOverview.getPercentInstitutions());
    }
}
