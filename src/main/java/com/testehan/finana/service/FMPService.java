package com.testehan.finana.service;

import com.testehan.finana.model.BalanceSheetReport;
import com.testehan.finana.model.CashFlowReport;
import com.testehan.finana.model.IncomeReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class FMPService {

    @Value("${fmp.api.key}")
    private String apiKey;

    private final WebClient webClient;

    public FMPService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://financialmodelingprep.com")
                .build();
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
}
