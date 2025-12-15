package com.testehan.finana.controller;

import com.testehan.deepresearch.model.JobResponse;
import com.testehan.deepresearch.model.JobStatusResponse;
import com.testehan.deepresearch.model.ResearchRequest;
import com.testehan.finana.service.DeepResearchService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/research")
public class DeepResearchController {

    private final DeepResearchService deepResearchService;

    public DeepResearchController(DeepResearchService deepResearchService) {
        this.deepResearchService = deepResearchService;
    }

    @PostMapping
    public Mono<JobResponse> createResearch(@RequestBody String stockTicker) {
        return deepResearchService.createResearch(stockTicker);
    }

    @GetMapping("/{jobId}")
    public Mono<JobStatusResponse> getResearchStatus(@PathVariable String jobId) {
        return deepResearchService.getResearchStatus(jobId);
    }
}
