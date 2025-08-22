package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AlphaVantageService {

    private final WebClient webClient;


    @Value("${alphavantage.api.key}")
    private String apiKey;

    @Autowired
    public AlphaVantageService(WebClient.Builder webClientBuilder,
                               CompanyOverviewRepository companyOverviewRepository,
                               IncomeStatementRepository incomeStatementRepository,
                               BalanceSheetRepository balanceSheetRepository,
                               CashFlowRepository cashFlowRepository,
                               SharesOutstandingRepository sharesOutstandingRepository) {
        this.webClient = webClientBuilder.baseUrl("https://www.alphavantage.co").build();
    }

    public Mono<IncomeStatementData> fetchIncomeStatementsFromApiAndSave(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "INCOME_STATEMENT")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(IncomeStatementData.class)
                .flatMap(incomeStatementData -> {
                    incomeStatementData.setSymbol(symbol);
                    return Mono.just(incomeStatementData);
                });
    }

    public Mono<BalanceSheetData> fetchBalanceSheetFromApiAndSave(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "BALANCE_SHEET")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(BalanceSheetData.class)
                .flatMap(balanceSheetData -> {
                    balanceSheetData.setSymbol(symbol);
                    return Mono.just(balanceSheetData);
                });
    }

    public Mono<CashFlowData> fetchCashFlowFromApiAndSave(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "CASH_FLOW")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(CashFlowData.class)
                .flatMap(cashFlowData -> {
                    cashFlowData.setSymbol(symbol);
                    return Mono.just(cashFlowData);
                });
    }

    public Mono<SharesOutstandingData> fetchSharesOutstandingFromApiAndSave(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "SHARES_OUTSTANDING")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(SharesOutstandingData.class)
                .flatMap(sharesOutstandingData -> {
                    sharesOutstandingData.setSymbol(symbol);
                    return Mono.just(sharesOutstandingData);
                });
    }

    public Mono<CompanyOverview> fetchCompanyOverviewFromApiAndSave(String symbol, Optional<CompanyOverview> existingOverview) {
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
                    return Mono.just(overviewToSave);
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
