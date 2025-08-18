package com.testehan.finana.service;

import com.testehan.finana.model.BalanceSheetData;
import com.testehan.finana.model.CashFlowData;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.IncomeStatementData;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.CashFlowRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
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
    private final CompanyOverviewRepository companyOverviewRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;

    @Value("${alphavantage.api.key}")
    private String apiKey;

    @Autowired
    public AlphaVantageService(WebClient.Builder webClientBuilder,
                               CompanyOverviewRepository companyOverviewRepository,
                               IncomeStatementRepository incomeStatementRepository,
                               BalanceSheetRepository balanceSheetRepository,
                               CashFlowRepository cashFlowRepository) {
        this.webClient = webClientBuilder.baseUrl("https://www.alphavantage.co").build();
        this.companyOverviewRepository = companyOverviewRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
    }

    public Mono<CompanyOverview> getCompanyOverview(String symbol) {
        return Mono.defer(() -> {
            Optional<CompanyOverview> overviewFromDb = companyOverviewRepository.findBySymbol(symbol.toUpperCase());
            if (overviewFromDb.isPresent() && isRecent(overviewFromDb.get().getLastUpdated())) {
                return Mono.just(overviewFromDb.get());
            } else {
                return fetchCompanyOverviewFromApiAndSave(symbol.toUpperCase(), overviewFromDb);
            }
        });
    }

    public Mono<IncomeStatementData> getIncomeStatements(String symbol) {
        return Mono.defer(() -> {
            Optional<IncomeStatementData> incomeStatementsFromDb = incomeStatementRepository.findBySymbol(symbol.toUpperCase());
            if (incomeStatementsFromDb.isPresent()) {
                return Mono.just(incomeStatementsFromDb.get());
            } else {
                return fetchIncomeStatementsFromApiAndSave(symbol.toUpperCase());
            }
        });
    }

    public Mono<BalanceSheetData> getBalanceSheet(String symbol) {
        return Mono.defer(() -> {
            Optional<BalanceSheetData> balanceSheetFromDb = balanceSheetRepository.findBySymbol(symbol.toUpperCase());
            if (balanceSheetFromDb.isPresent()) {
                return Mono.just(balanceSheetFromDb.get());
            } else {
                return fetchBalanceSheetFromApiAndSave(symbol.toUpperCase());
            }
        });
    }

    public Mono<CashFlowData> getCashFlow(String symbol) {
        return Mono.defer(() -> {
            Optional<CashFlowData> cashFlowFromDb = cashFlowRepository.findBySymbol(symbol.toUpperCase());
            if (cashFlowFromDb.isPresent()) {
                return Mono.just(cashFlowFromDb.get());
            } else {
                return fetchCashFlowFromApiAndSave(symbol.toUpperCase());
            }
        });
    }

    private Mono<IncomeStatementData> fetchIncomeStatementsFromApiAndSave(String symbol) {
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
                    return Mono.just(incomeStatementRepository.save(incomeStatementData));
                });
    }

    private Mono<BalanceSheetData> fetchBalanceSheetFromApiAndSave(String symbol) {
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
                    return Mono.just(balanceSheetRepository.save(balanceSheetData));
                });
    }

    private Mono<CashFlowData> fetchCashFlowFromApiAndSave(String symbol) {
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
                    return Mono.just(cashFlowRepository.save(cashFlowData));
                });
    }

    private boolean isRecent(LocalDateTime lastUpdated) {
        if (lastUpdated == null) {
            return false;
        }
        return ChronoUnit.WEEKS.between(lastUpdated, LocalDateTime.now()) < 1;
    }

    private Mono<CompanyOverview> fetchCompanyOverviewFromApiAndSave(String symbol, Optional<CompanyOverview> existingOverview) {
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
                    return Mono.just(companyOverviewRepository.save(overviewToSave));
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
