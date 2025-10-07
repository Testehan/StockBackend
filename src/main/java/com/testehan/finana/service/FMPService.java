package com.testehan.finana.service;

import com.testehan.finana.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    public Mono<List<IndexData>> getIndexHistoricalData(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/historical-price-eod/light")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<IndexData>>() {})
                .onErrorResume(e -> {
                    LOGGER.error("Error fetching index historical data for symbol: " + symbol, e);
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


    public Mono<List<SecFilingUrlData>> getSecFilings(String symbol) {
        String to = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String from = LocalDate.now().minusMonths(18).format(DateTimeFormatter.ISO_LOCAL_DATE);

        return fetchSecFilingsPage(symbol, from, to, 0);
    }

    private Mono<List<SecFilingUrlData>> fetchSecFilingsPage(String symbol, String from, String to, int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/sec-filings-search/symbol")
                        .queryParam("symbol", symbol)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("page", page)
                        .queryParam("limit", 100)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<SecFilingUrlData>>() {})
                .flatMap(filings -> {
                    if (filings.isEmpty()) {
                        return Mono.just(java.util.Collections.<SecFilingUrlData>emptyList());
                    } else {
                        return fetchSecFilingsPage(symbol, from, to, page + 1)
                                .map(nextPageFilings -> {
                                    List<SecFilingUrlData> allFilings = new java.util.ArrayList<>(filings);
                                    allFilings.addAll(nextPageFilings);
                                    return allFilings;
                                });
                    }
                })
                .onErrorResume(e -> {
                    LOGGER.error("Error fetching SEC filings for symbol: " + symbol + " on page " + page, e);
                    return Mono.just(java.util.Collections.<SecFilingUrlData>emptyList());
                });
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

    public Mono<EarningsHistory> fetchEarningsHistory(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/earnings")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<FmpEarning>>() {})
                .map(fmpEarnings -> {
                    EarningsHistory earningsHistory = new EarningsHistory();
                    earningsHistory.setSymbol(symbol);
                    List<QuarterlyEarning> quarterlyEarnings = fmpEarnings.stream()
                            .map(this::transformToQuarterlyEarning)
                            .collect(java.util.stream.Collectors.toList());
                    earningsHistory.setQuarterlyEarnings(quarterlyEarnings);
                    return earningsHistory;
                });
    }

    private QuarterlyEarning transformToQuarterlyEarning(FmpEarning fmpEarning) {
        QuarterlyEarning quarterlyEarning = new QuarterlyEarning();
        quarterlyEarning.setFiscalDateEnding(fmpEarning.getDate());
        if (fmpEarning.getEpsActual() != null) {
            quarterlyEarning.setReportedEPS(fmpEarning.getEpsActual().toString());
        }
        if (fmpEarning.getEpsEstimated() != null) {
            quarterlyEarning.setEstimatedEPS(fmpEarning.getEpsEstimated().toString());
        }

        if (fmpEarning.getEpsActual() != null && fmpEarning.getEpsEstimated() != null) {
            double surprise = fmpEarning.getEpsActual() - fmpEarning.getEpsEstimated();
            quarterlyEarning.setSurprise(String.valueOf(surprise));

            if (fmpEarning.getEpsEstimated() != 0) {
                double surprisePercentage = (surprise / fmpEarning.getEpsEstimated()) * 100;
                quarterlyEarning.setSurprisePercentage(String.valueOf(surprisePercentage));
            }
        }

        return quarterlyEarning;
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

    public Mono<List<Estimate>> fetchAnalystEstimates(String symbol) {
        LOGGER.info("Fetching analyst estimates for {}", symbol);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/stable/analyst-estimates")
                        .queryParam("symbol", symbol)
                        .queryParam("period", "annual")
                        .queryParam("limit", 10)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Estimate>>() {});
    }
}
