package com.testehan.finana.service;

import com.testehan.deepresearch.model.JobResponse;
import com.testehan.deepresearch.model.JobStatusResponse;
import com.testehan.deepresearch.model.ResearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class DeepResearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepResearchService.class);
    private final WebClient webClient;

    @Value("classpath:/prompts/deepresearch/discovery_prompt.txt")
    private Resource discoveryPromptResource;

    @Value("classpath:/prompts/deepresearch/synthesis_prompt.txt")
    private Resource synthesisPromptResource;

    @Value("classpath:/prompts/deepresearch/compile_report_prompt.txt")
    private Resource compileReportPromptResource;

    public DeepResearchService(WebClient.Builder webClientBuilder,
                               @Value("${deepresearch.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public Mono<JobResponse> createResearch(String stockTicker) {
        try {
            String discoveryPrompt = StreamUtils.copyToString(discoveryPromptResource.getInputStream(), StandardCharsets.UTF_8);
            String synthesisPrompt = StreamUtils.copyToString(synthesisPromptResource.getInputStream(), StandardCharsets.UTF_8);
            String compileReportPrompt = StreamUtils.copyToString(compileReportPromptResource.getInputStream(), StandardCharsets.UTF_8);

            ResearchRequest request = new ResearchRequest(
                    String.format("%s stock news and developments from the last month", stockTicker),
                    20,
                    5,
                    discoveryPrompt,
                    synthesisPrompt,
                    compileReportPrompt
            );

            return createResearch(request);
        } catch (IOException e) {
            LOGGER.error("Error loading prompts: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    private Mono<JobResponse> createResearch(ResearchRequest request) {
        LOGGER.info("Creating research job for topic: {}", request.topic());
        return webClient.post()
                .uri("/api/research")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JobResponse.class)
                .doOnError(error -> LOGGER.error("Error creating research job: {}", error.getMessage()));
    }

    public Mono<JobStatusResponse> getResearchStatus(String jobId) {
        LOGGER.info("Getting research status for jobId: {}", jobId);
        return webClient.get()
                .uri("/api/research/{jobId}", jobId)
                .retrieve()
                .bodyToMono(JobStatusResponse.class)
                .doOnError(error -> LOGGER.error("Error getting research status for jobId {}: {}", jobId, error.getMessage()));
    }
}
