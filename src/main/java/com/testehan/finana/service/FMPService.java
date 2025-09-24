package com.testehan.finana.service;

import com.testehan.finana.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FMPService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FMPService.class);

    @Value("${fmp.api.key}")
    private String apiKey;

    private final WebClient webClient;

    public FMPService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://financialmodelingprep.com")
                .build();
    }

    public Mono<List<GlobalQuote>> getHistoricalDividendAdjustedEodPrice(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/historical-price-eod/dividend-adjusted")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<GlobalQuote>>() {})
                .onErrorResume(e -> {
                    LOGGER.error("Error fetching historical dividend adjusted EOD price for symbol: " + symbol, e);
                    return Mono.just(java.util.Collections.emptyList());
                });
    }

    public Mono<List<IncomeReport>> getIncomeStatement(String symbol,String period) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/income-statement")
                        .queryParam("symbol", symbol)
                        .queryParam("period", period)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<IncomeReport>>() {});
    }

    public Mono<List<BalanceSheetReport>> getBalanceSheetStatement(String symbol,String period) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/balance-sheet-statement")
                        .queryParam("symbol", symbol)
                        .queryParam("period", period)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<BalanceSheetReport>>() {});
    }

    public Mono<List<CashFlowReport>> getCashflowStatement(String symbol,String period) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/cash-flow-statement")
                        .queryParam("symbol", symbol)
                        .queryParam("period", period)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<CashFlowReport>>() {});
    }

    public Mono<List<RevenueSegmentationReport>> getRevenueSegmentation(String symbol, String period) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/revenue-product-segmentation")
                        .queryParam("symbol", symbol)
                        .queryParam("period", period)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<RevenueSegmentationReport>>() {});
    }

    public Mono<List<RevenueGeographicSegmentationReport>> getRevenueGeographicSegmentation(String symbol, String period) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/revenue-geographic-segmentation")
                        .queryParam("symbol", symbol)
                        .queryParam("period", period)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<RevenueGeographicSegmentationReport>>() {});
    }

    public Mono<CompanyOverview> getCompanyOverview(String symbol, Optional<CompanyOverview> existingOverview) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/profile")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build(symbol))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<CompanyOverview>>() {})
                .flatMap(overviews -> {
                    if (overviews.isEmpty()) {
                        return Mono.empty();
                    }
                    CompanyOverview newOverview = overviews.get(0);
                    CompanyOverview overviewToSave = existingOverview.orElse(new CompanyOverview());
                    updateOverview(overviewToSave, newOverview);
                    overviewToSave.setLastUpdated(LocalDateTime.now());
                    return Mono.just(overviewToSave);
                });
    }

    public void updateOverview(CompanyOverview target, CompanyOverview source) {
        target.setSymbol(source.getSymbol());
        target.setPrice(source.getPrice());
        target.setMarketCap(source.getMarketCap());
        target.setBeta(source.getBeta());
        target.setLastDividend(source.getLastDividend());
        target.setRange(source.getRange());
        target.setChange(source.getChange());
        target.setChangePercentage(source.getChangePercentage());
        target.setVolume(source.getVolume());
        target.setAverageVolume(source.getAverageVolume());
        target.setCompanyName(source.getCompanyName());
        target.setCurrency(source.getCurrency());
        target.setCik(source.getCik());
        target.setIsin(source.getIsin());
        target.setCusip(source.getCusip());
        target.setExchangeFullName(source.getExchangeFullName());
        target.setExchange(source.getExchange());
        target.setIndustry(source.getIndustry());
        target.setWebsite(source.getWebsite());
        target.setDescription(source.getDescription());
        target.setCeo(source.getCeo());
        target.setSector(source.getSector());
        target.setCountry(source.getCountry());
        target.setFullTimeEmployees(source.getFullTimeEmployees());
        target.setPhone(source.getPhone());
        target.setAddress(source.getAddress());
        target.setCity(source.getCity());
        target.setState(source.getState());
        target.setZip(source.getZip());
        target.setImage(source.getImage());
        target.setIpoDate(source.getIpoDate());
        target.setDefaultImage(source.isDefaultImage());
        target.setEtf(source.isEtf());
        target.setActivelyTrading(source.isActivelyTrading());
        target.setAdr(source.isAdr());
        target.setFund(source.isFund());
    }
}
