package com.testehan.finana.controller;

import com.testehan.finana.service.SecApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/stocks/sec")
public class SecController {

    private final SecApiService secApiService;

    @Autowired
    public SecController(SecApiService secApiService) {
        this.secApiService = secApiService;
    }

    @GetMapping("/10-k/{ticker}/risk-factors")
    public Mono<String> getRiskFactorsFrom10K(@PathVariable String ticker) {
        return secApiService.getSectionFrom10K(ticker, "risk_factors");
    }

    @GetMapping("/10-k/{ticker}/management-discussion")
    public Mono<String> getManagementDiscussionFrom10K(@PathVariable String ticker) {
        return secApiService.getSectionFrom10K(ticker, "management_discussion");
    }

    @GetMapping("/10-k/{ticker}/business-description")
    public Mono<String> getBusinessDescriptionFrom10K(@PathVariable String ticker) {
        return secApiService.getSectionFrom10K(ticker, "business_description");
    }

    @GetMapping("/10-q/{ticker}/management-discussion")
    public Mono<String> getManagementDiscussionFrom10Q(@PathVariable String ticker) {
        return secApiService.getSectionFrom10Q(ticker, "management_discussion");
    }

    @GetMapping("/10-q/{ticker}/risk-factors")
    public Mono<String> getRiskFactorsFrom10Q(@PathVariable String ticker) {
        return secApiService.getSectionFrom10Q(ticker, "risk_factors");
    }
}
