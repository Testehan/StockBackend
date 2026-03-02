package com.testehan.finana.service;

import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.ratio.FmpRatios;
import com.testehan.finana.model.ratio.FmpRatiosTtm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FMPServiceTest {

    private FMPService fmpService;
    private final AtomicReference<String> nextResponseBody = new AtomicReference<>("[]");

    @BeforeEach
    void setUp() {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(nextResponseBody.get())
                        .build()
        );
        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction);
        fmpService = new FMPService(webClientBuilder, "http://localhost/");
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void getHistoricalDividendAdjustedEodPrice_returnsData() {
        String symbol = "AAPL";
        String responseBody = "[{\"symbol\":\"AAPL\",\"date\":\"2023-01-01\",\"adjClose\":\"150.0\"}]";
        nextResponseBody.set(responseBody);

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
        nextResponseBody.set(responseBody);

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
        nextResponseBody.set(responseBody);

        Mono<FmpRatiosTtm> result = fmpService.getFinancialRatiosTtm(symbol);

        StepVerifier.create(result)
                .assertNext(ratios -> {
                    assertEquals(16.5, ratios.getPriceToEarningsRatioTTM());
                })
                .verifyComplete();
    }
}
