package com.testehan.finana.controller;

import com.testehan.finana.service.DeepResearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/research")
public class DeepResearchController {

    private final DeepResearchService deepResearchService;

    public DeepResearchController(DeepResearchService deepResearchService) {
        this.deepResearchService = deepResearchService;
    }

    @GetMapping("/{stockTicker}")
    public Mono<ResponseEntity<?>> getResearch(@PathVariable String stockTicker) {
        return deepResearchService.getResearchReport(stockTicker.toUpperCase())
                .map(report -> {
                    if (report != null) {
                        return ResponseEntity.ok(report);
                    } else {
                        return ResponseEntity.ok().body("No deep research report found for " + stockTicker.toUpperCase());
                    }
                });
    }
}
