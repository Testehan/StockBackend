package com.testehan.finana.service;

import com.testehan.finana.model.filing.CompanyEarningsTranscripts;
import com.testehan.finana.model.filing.EarningsCallTranscript;
import com.testehan.finana.model.filing.QuarterlyEarningsTranscript;
import com.testehan.finana.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class AlphaVantageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlphaVantageService.class);
    private final WebClient webClient;


    @Value("${alphavantage.api.key}")
    private String apiKey;

    private final CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository;

    public AlphaVantageService(WebClient.Builder webClientBuilder,
                               CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository) {
        this(webClientBuilder, companyEarningsTranscriptsRepository, "https://www.alphavantage.co");
    }

    public AlphaVantageService(WebClient.Builder webClientBuilder,
                               CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository,
                               String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.companyEarningsTranscriptsRepository = companyEarningsTranscriptsRepository;
    }

    public Mono<CompanyEarningsTranscripts> fetchEarningsCallTranscriptFromApiAndSave(String symbol, String quarter) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "EARNINGS_CALL_TRANSCRIPT")
                        .queryParam("symbol", symbol)
                        .queryParam("quarter", quarter)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(EarningsCallTranscript.class)
                .flatMap(earningsCallTranscript -> {
                    QuarterlyEarningsTranscript newQuarterlyTranscript = new QuarterlyEarningsTranscript();
                    newQuarterlyTranscript.setQuarter(earningsCallTranscript.getQuarter());
                    newQuarterlyTranscript.setTranscript(earningsCallTranscript.getTranscript());
                    
                    if (earningsCallTranscript.getTranscript() != null && !earningsCallTranscript.getTranscript().isEmpty()) {
                        newQuarterlyTranscript.setLastUpdated(LocalDateTime.now());
                    }

                    return Mono.fromCallable(() -> companyEarningsTranscriptsRepository.findById(symbol))
                            .flatMap(optionalCompanyEarningsTranscripts -> {
                                CompanyEarningsTranscripts companyEarningsTranscripts = optionalCompanyEarningsTranscripts.orElseGet(() -> {
                                    CompanyEarningsTranscripts newCompanyEarningsTranscripts = new CompanyEarningsTranscripts();
                                    newCompanyEarningsTranscripts.setSymbol(symbol);
                                    return newCompanyEarningsTranscripts;
                                });

                                boolean transcriptExists = companyEarningsTranscripts.getTranscripts() != null && companyEarningsTranscripts.getTranscripts().stream()
                                        .anyMatch(transcript -> {
                                         if (Objects.nonNull(transcript.getQuarter()))
                                         {
                                             return   transcript.getQuarter().equals(quarter);
                                         } else {
                                             return false;
                                         }
                                        });

                                if (!transcriptExists) {
                                    if (companyEarningsTranscripts.getTranscripts() == null) {
                                        companyEarningsTranscripts.setTranscripts(new java.util.ArrayList<>());
                                    }
                                    companyEarningsTranscripts.getTranscripts().add(newQuarterlyTranscript);
                                    return Mono.fromCallable(() -> companyEarningsTranscriptsRepository.save(companyEarningsTranscripts));
                                } else {
                                    return Mono.just(companyEarningsTranscripts);
                                }
                            });
                });
    }

}
