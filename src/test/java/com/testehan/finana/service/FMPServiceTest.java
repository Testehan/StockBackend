package com.testehan.finana.service;

import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.ratio.FmpRatios;
import com.testehan.finana.model.ratio.FmpRatiosTtm;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FMPServiceTest {

    private MockWebServer mockWebServer;
    private FMPService fmpService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient.Builder webClientBuilder = WebClient.builder();
        fmpService = new FMPService(webClientBuilder, mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getHistoricalDividendAdjustedEodPrice_returnsData() {
        String symbol = "AAPL";
        String responseBody = "[{\"symbol\":\"AAPL\",\"date\":\"2023-01-01\",\"adjClose\":\"150.0\"}]";
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        Mono<List<GlobalQuote>> result = fmpService.getHistoricalDividendAdjustedEodPrice(symbol);

        StepVerifier.create(result)
                .assertNext(quotes -> {
                    assertEquals(1, quotes.size());
                    assertEquals("150.0", quotes.get(0).getAdjClose());
                })
                .verifyComplete();
    }

    @Test
    void getFinancialRatios_returnsData() {
        String symbol = "AAPL";
        String responseBody = "[{\"symbol\":\"AAPL\",\"priceToEarningsRatio\":15.5}]";
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        Mono<List<FmpRatios>> result = fmpService.getFinancialRatios(symbol);

        StepVerifier.create(result)
                .assertNext(ratios -> {
                    assertEquals(1, ratios.size());
                    assertEquals(15.5, ratios.get(0).getPriceToEarningsRatio());
                })
                .verifyComplete();
    }

    @Test
    void getFinancialRatiosTtm_returnsData() {
        String symbol = "AAPL";
        String responseBody = "[{\"priceToEarningsRatioTTM\":16.5}]";
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        Mono<FmpRatiosTtm> result = fmpService.getFinancialRatiosTtm(symbol);

        StepVerifier.create(result)
                .assertNext(ratios -> {
                    assertEquals(16.5, ratios.getPriceToEarningsRatioTTM());
                })
                .verifyComplete();
    }
}
