package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.model.filing.SecFilingUrlData;
import com.testehan.finana.model.finstatement.*;
import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.quote.IndexData;
import com.testehan.finana.model.ratio.FmpRatios;
import com.testehan.finana.model.ratio.FmpRatiosTtm;
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
import java.util.Set;

@Service
public class FMPService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FMPService.class);
    private static final Set<String> ALLOWED_FORM_TYPES = Set.of("10-K", "20-F", "10-Q", "6-K");


    @Value("${fmp.api.key}")
    private String apiKey;

    private final WebClient webClient;

    public FMPService(WebClient.Builder webClientBuilder) {
        this(webClientBuilder, "https://financialmodelingprep.com");
    }

    public FMPService(WebClient.Builder webClientBuilder, String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
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
                    LOGGER.error("Error fetching historical dividend adjusted EOD price for symbol: " + symbol);
                    return Mono.just(java.util.Collections.<GlobalQuote>emptyList());
                });
    }

    public Mono<List<FmpRatios>> getFinancialRatios(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/ratios")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<FmpRatios>>() {});
    }

    public Mono<FmpRatiosTtm> getFinancialRatiosTtm(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stable/ratios-ttm")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<FmpRatiosTtm>>() {})
                .map(list -> list.get(0));
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
                    LOGGER.error("Error fetching index historical data for symbol: " + symbol);
                    return Mono.just(java.util.Collections.<IndexData>emptyList());
                });
    }

    public Mono<List<IncomeReport>> getIncomeStatement(String symbol, String period) {
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

    public Mono<List<BalanceSheetReport>> getBalanceSheetStatement(String symbol, String period) {
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

    public Mono<List<CashFlowReport>> getCashflowStatement(String symbol, String period) {
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
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(18);

        return fetchSecFilingsPage(symbol, from.format(DateTimeFormatter.ISO_LOCAL_DATE), to.format(DateTimeFormatter.ISO_LOCAL_DATE), 0)
                .flatMap(filings -> {
                    Set<String> annualReports = Set.of("10-K", "20-F");
                    Set<String> quarterlyReports = Set.of("10-Q", "6-K");

                    boolean hasAnnual = filings.stream().anyMatch(f -> annualReports.contains(f.getFormType()));
                    boolean hasQuarterly = filings.stream().anyMatch(f -> quarterlyReports.contains(f.getFormType()));

                    if (hasAnnual && hasQuarterly) {
                        return Mono.just(filings);
                    } else {
                        LOGGER.info("Initial fetch for {} did not contain all required SEC filing types. Fetching for an earlier period.", symbol);
                        LocalDate earlierTo = from.minusDays(1);
                        LocalDate earlierFrom = earlierTo.minusMonths(18);

                        return fetchSecFilingsPage(symbol, earlierFrom.format(DateTimeFormatter.ISO_LOCAL_DATE), earlierTo.format(DateTimeFormatter.ISO_LOCAL_DATE), 0)
                                .map(earlierFilings -> {
                                    List<SecFilingUrlData> combined = new java.util.ArrayList<>(filings);
                                    combined.addAll(earlierFilings);
                                    return combined;
                                });
                    }
                });
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
                    }

                    List<SecFilingUrlData> filteredFilings = filings.stream()
                            .filter(filing -> ALLOWED_FORM_TYPES.contains(filing.getFormType()))
                            .collect(java.util.stream.Collectors.toList());

                    return fetchSecFilingsPage(symbol, from, to, page + 1)
                            .map(nextPageFilings -> {
                                List<SecFilingUrlData> allFilings = new java.util.ArrayList<>(filteredFilings);
                                allFilings.addAll(nextPageFilings);
                                return allFilings;
                            });
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
                    earningsHistory.setLastUpdated(LocalDateTime.now());
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
