package com.testehan.finana.controller;

import com.testehan.finana.model.FerolLlmResponse;
import com.testehan.finana.service.reporting.FerolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/stocks/reporting")
public class ReportingController {

    private final FerolService ferolService;

    @Autowired
    public ReportingController(FerolService ferolService) {
        this.ferolService = ferolService;
    }

    @GetMapping("/ferol/{ticker}")
    public Mono<FerolLlmResponse> getFerolReport(@PathVariable String ticker) {
        return Mono.just(ferolService.financialResilience(ticker.toUpperCase()));
    }
}
