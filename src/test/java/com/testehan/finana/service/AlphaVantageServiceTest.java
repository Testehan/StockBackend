package com.testehan.finana.service;

import com.testehan.finana.model.filing.CompanyEarningsTranscripts;
import com.testehan.finana.repository.CompanyEarningsTranscriptsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlphaVantageServiceTest {

    private AlphaVantageService alphaVantageService;
    private final AtomicReference<String> nextResponseBody = new AtomicReference<>("");

    @Mock
    private CompanyEarningsTranscriptsRepository repository;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(nextResponseBody.get())
                        .build()
        );
        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction);
        alphaVantageService = new AlphaVantageService(webClientBuilder, repository, "http://localhost/");
    }

    @AfterEach
    void tearDown() {
        try {
            closeable.close();
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    void fetchEarningsCallTranscriptFromApiAndSave_savesNewTranscript() {
        String symbol = "AAPL";
        String quarter = "Q1-2023";
        String transcriptContent = "This is a transcript";
        
        nextResponseBody.set("{\"symbol\":\"AAPL\",\"quarter\":\"Q1-2023\",\"transcript\":[{\"content\":\"This is a transcript\"}]}");

        when(repository.findById(symbol)).thenReturn(Optional.empty());
        when(repository.save(any(CompanyEarningsTranscripts.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mono<CompanyEarningsTranscripts> result = alphaVantageService.fetchEarningsCallTranscriptFromApiAndSave(symbol, quarter);

        StepVerifier.create(result)
                .assertNext(saved -> {
                    assertEquals(symbol, saved.getSymbol());
                    assertEquals(1, saved.getTranscripts().size());
                    assertEquals(quarter, saved.getTranscripts().get(0).getQuarter());
                    assertEquals(transcriptContent, saved.getTranscripts().get(0).getTranscript().get(0).getContent());
                })
                .verifyComplete();

        verify(repository).findById(symbol);
        verify(repository).save(any(CompanyEarningsTranscripts.class));
    }

    @Test
    void fetchEarningsCallTranscriptFromApiAndSave_doesNotSaveIfTranscriptExists() {
        String symbol = "AAPL";
        String quarter = "Q1-2023";
        
        nextResponseBody.set("{\"symbol\":\"AAPL\",\"quarter\":\"Q1-2023\",\"transcript\":[{\"content\":\"This is a transcript\"}]}");

        CompanyEarningsTranscripts existing = new CompanyEarningsTranscripts();
        existing.setSymbol(symbol);
        com.testehan.finana.model.filing.QuarterlyEarningsTranscript existingTranscript = new com.testehan.finana.model.filing.QuarterlyEarningsTranscript();
        existingTranscript.setQuarter(quarter);
        existing.setTranscripts(new java.util.ArrayList<>(java.util.List.of(existingTranscript)));

        when(repository.findById(symbol)).thenReturn(Optional.of(existing));

        Mono<CompanyEarningsTranscripts> result = alphaVantageService.fetchEarningsCallTranscriptFromApiAndSave(symbol, quarter);

        StepVerifier.create(result)
                .assertNext(saved -> {
                    assertEquals(1, saved.getTranscripts().size());
                })
                .verifyComplete();

        verify(repository).findById(symbol);
        verify(repository, never()).save(any(CompanyEarningsTranscripts.class));
    }
}
