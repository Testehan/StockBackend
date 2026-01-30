package com.testehan.finana.service;

import com.testehan.finana.model.filing.CompanyEarningsTranscripts;
import com.testehan.finana.model.filing.EarningsCallTranscript;
import com.testehan.finana.repository.CompanyEarningsTranscriptsRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlphaVantageServiceTest {

    private MockWebServer mockWebServer;
    private AlphaVantageService alphaVantageService;

    @Mock
    private CompanyEarningsTranscriptsRepository repository;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() throws IOException {
        closeable = MockitoAnnotations.openMocks(this);
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient.Builder webClientBuilder = WebClient.builder();
        alphaVantageService = new AlphaVantageService(webClientBuilder, repository, mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
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
        
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"symbol\":\"AAPL\",\"quarter\":\"Q1-2023\",\"transcript\":[{\"content\":\"This is a transcript\"}]}")
                .addHeader("Content-Type", "application/json"));

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
        
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"symbol\":\"AAPL\",\"quarter\":\"Q1-2023\",\"transcript\":[{\"content\":\"This is a transcript\"}]}")
                .addHeader("Content-Type", "application/json"));

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
