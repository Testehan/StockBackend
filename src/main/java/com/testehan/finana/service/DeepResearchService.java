package com.testehan.finana.service;

import com.testehan.deepresearch.model.JobResponse;
import com.testehan.deepresearch.model.ResearchJob.JobStatus;
import com.testehan.deepresearch.model.JobStatusResponse;
import com.testehan.deepresearch.model.ResearchJob;
import com.testehan.deepresearch.model.ResearchReport;
import com.testehan.deepresearch.model.ResearchRequest;
import com.testehan.finana.model.reporting.DeepResearchReport;
import com.testehan.finana.repository.DeepResearchReportRepository;
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
import java.time.LocalDateTime;

@Service
public class DeepResearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepResearchService.class);
    private final WebClient webClient;
    private final DeepResearchReportRepository deepResearchReportRepository;

    @Value("classpath:/prompts/deepresearch/discovery_prompt.txt")
    private Resource discoveryPromptResource;

    @Value("classpath:/prompts/deepresearch/synthesis_prompt.txt")
    private Resource synthesisPromptResource;

    @Value("classpath:/prompts/deepresearch/compile_report_prompt.txt")
    private Resource compileReportPromptResource;

    public DeepResearchService(WebClient.Builder webClientBuilder,
                               DeepResearchReportRepository deepResearchReportRepository,
                               @Value("${deepresearch.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.deepResearchReportRepository = deepResearchReportRepository;
    }

    public Mono<DeepResearchReport> getResearchReport(String stockTicker) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return Mono.fromCallable(() -> deepResearchReportRepository.findFirstByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(stockTicker.toUpperCase(), thirtyDaysAgo))
                .flatMap(optionalReport -> {
                    if (optionalReport.isPresent()) {
                        return Mono.just(optionalReport.get());
                    } else {
                        return Mono.empty();
                    }
                });
    }

    public Mono<JobResponse> triggerNewResearch(String stockTicker) {
        try {
            String discoveryPrompt = StreamUtils.copyToString(discoveryPromptResource.getInputStream(), StandardCharsets.UTF_8);
            String synthesisPrompt = StreamUtils.copyToString(synthesisPromptResource.getInputStream(), StandardCharsets.UTF_8);
            String compileReportPrompt = StreamUtils.copyToString(compileReportPromptResource.getInputStream(), StandardCharsets.UTF_8);

            String topic = String.format("%s stock news and developments from the last month", stockTicker.toUpperCase());
            ResearchRequest request = new ResearchRequest(
                    topic,
                    25,
                    8,
                    discoveryPrompt,
                    synthesisPrompt,
                    compileReportPrompt
            );

            return webClient.post()
                    .uri("/api/research")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JobResponse.class)
                    .doOnNext(response -> {
                        DeepResearchReport report = new DeepResearchReport();
                        report.setJobId(response.jobId());
                        report.setStockTicker(stockTicker.toUpperCase());
                        report.setTopic(response.topic());
                        report.setStatus(response.status().toString());
                        report.setCreatedAt(LocalDateTime.now());
                        deepResearchReportRepository.save(report);
                        LOGGER.info("Saved initial deep research report record for jobId: {}", response.jobId());
                    })
                    .doOnError(error -> LOGGER.error("Error creating research job: {}", error.getMessage()));
        } catch (IOException e) {
            LOGGER.error("Error loading prompts: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    public Mono<JobStatusResponse> getJobStatus(String jobId) {
        return Mono.fromCallable(() -> deepResearchReportRepository.findById(jobId))
                .flatMap(optionalReport -> {
                    if (optionalReport.isPresent() && JobStatus.COMPLETED.toString().equals(optionalReport.get().getStatus())) {
                        DeepResearchReport dbReport = optionalReport.get();
                        ResearchReport researchReport = dbReport.getReport();
                        if (researchReport != null) {
                            ResearchJob.JobResult result = new ResearchJob.JobResult(
                                    researchReport.executiveSummary(),
                                    researchReport.keyFindings(),
                                    researchReport.themes(),
                                    researchReport.openQuestions(),
                                    researchReport.sources(),
                                    researchReport.diagnostics()
                            );
                            return Mono.just(new JobStatusResponse(JobStatus.fromValue(dbReport.getStatus()).toString(), result, null));
                        }
                        return fetchStatusFromExternalService(jobId, dbReport);
                    } else {
                        return fetchStatusFromExternalService(jobId, optionalReport.orElse(null));
                    }
                });
    }

    private Mono<JobStatusResponse> fetchStatusFromExternalService(String jobId, DeepResearchReport existingReport) {
        LOGGER.info("Fetching research status for jobId: {} from external service", jobId);
        return webClient.get()
                .uri("/api/research/{jobId}", jobId)
                .retrieve()
                .bodyToMono(JobStatusResponse.class)
                .doOnNext(response -> {
                    if (JobStatus.COMPLETED.toString().equals(response.status())) {
                        saveCompletedReport(jobId, response, existingReport);
                    } else if (JobStatus.FAILED.toString().equals(response.status())) {
                        updateFailedReport(jobId, response, existingReport);
                    }
                })
                .doOnError(error -> LOGGER.error("Error getting research status for jobId {}: {}", jobId, error.getMessage()));
    }

    private void saveCompletedReport(String jobId, JobStatusResponse response, DeepResearchReport existingReport) {
        DeepResearchReport dbReport = existingReport != null ? existingReport : new DeepResearchReport();
        dbReport.setJobId(jobId);
        dbReport.setStatus(response.status().toString());

        ResearchJob.JobResult jobResult = response.result();
        if (jobResult != null) {
            ResearchReport researchReport = new ResearchReport(
                    dbReport.getTopic(),
                    jobResult.executiveSummary(),
                    jobResult.keyFindings(),
                    jobResult.themes(),
                    jobResult.openQuestions(),
                    jobResult.sources(),
                    jobResult.diagnostics()
            );
            dbReport.setReport(researchReport);
        }

        if (dbReport.getCreatedAt() == null) {
            dbReport.setCreatedAt(LocalDateTime.now());
        }
        deepResearchReportRepository.save(dbReport);
        LOGGER.info("Saved completed deep research report for jobId: {}", jobId);
    }

    private void updateFailedReport(String jobId, JobStatusResponse response, DeepResearchReport existingReport) {
        if (existingReport != null) {
            existingReport.setStatus(response.status().toString());
            deepResearchReportRepository.save(existingReport);
            LOGGER.info("Updated status to FAILED for jobId: {}", jobId);
        }
    }
}
