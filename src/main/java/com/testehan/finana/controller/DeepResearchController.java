package com.testehan.finana.controller;

import com.testehan.deepresearch.model.JobStatusResponse;
import com.testehan.finana.model.reporting.DeepResearchReport;
import com.testehan.finana.service.DeepResearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
        return deepResearchService.getResearchReport(stockTicker)
                .map(report -> {
                    if (report != null) {
                        return ResponseEntity.ok(report);
                    } else {
                        return ResponseEntity.ok().body("No deep research report found for " + stockTicker.toUpperCase());
                    }
                });
    }
}
