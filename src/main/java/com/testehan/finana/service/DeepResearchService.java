package com.testehan.finana.service;

import com.testehan.deepresearch.model.*;
import com.testehan.deepresearch.model.ResearchJob.JobStatus;
import com.testehan.finana.model.reporting.EarningsPresentationReportEntity;
import com.testehan.finana.model.reporting.NewsReportEntity;
import com.testehan.finana.model.research.ResearchJobRecord;
import com.testehan.finana.repository.EarningsPresentationReportRepository;
import com.testehan.finana.repository.NewsReportRepository;
import com.testehan.finana.repository.ResearchJobRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeepResearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepResearchService.class);
    private final WebClient webClient;
    private final NewsReportRepository newsReportRepository;
    private final EarningsPresentationReportRepository earningsPresentationReportRepository;
    private final ResearchJobRecordRepository researchJobRecordRepository;

    @Value("classpath:/prompts/deepresearch/discovery_prompt.txt")
    private Resource discoveryPromptResource;

    @Value("classpath:/prompts/deepresearch/synthesis_prompt.txt")
    private Resource synthesisPromptResource;

    @Value("classpath:/prompts/deepresearch/compile_report_prompt.txt")
    private Resource compileReportPromptResource;

    @Value("classpath:/prompts/deepresearch/document/per_document_prompt.txt")
    private Resource perDocumentPromptResource;

    @Value("classpath:/prompts/deepresearch/document/per_page_prompt.txt")
    private Resource perPagePromptResource;

    public DeepResearchService(WebClient.Builder webClientBuilder,
                               NewsReportRepository newsReportRepository,
                               EarningsPresentationReportRepository earningsPresentationReportRepository,
                               ResearchJobRecordRepository researchJobRecordRepository,
                               @Value("${deepresearch.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.newsReportRepository = newsReportRepository;
        this.earningsPresentationReportRepository = earningsPresentationReportRepository;
        this.researchJobRecordRepository = researchJobRecordRepository;
    }

    public List<ResearchJobRecord> getRunningJobs() {
        return researchJobRecordRepository.findAllByStatus(JobStatus.RUNNING.toString());
    }

    public void trackJob(String jobId, String ticker, ResearchTopic topic) {
        ResearchJobRecord record = new ResearchJobRecord();
        record.setJobId(jobId);
        record.setStockTicker(ticker);
        record.setTopic(topic);
        record.setStatus(JobStatus.RUNNING.toString());
        record.setCreatedAt(LocalDateTime.now());
        researchJobRecordRepository.save(record);
        LOGGER.info("Tracking job {} for ticker {} with topic {}", jobId, ticker, topic);
    }

    public Mono<NewsReportEntity> getNewsReport(String stockTicker) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LOGGER.info("Looking for NewsReport with ticker={} and createdAt after {}", stockTicker, thirtyDaysAgo);
        return Mono.fromCallable(() -> newsReportRepository.findFirstByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(stockTicker, thirtyDaysAgo))
                .flatMap(optionalReport -> {
                    if (optionalReport.isPresent()) {
                        return Mono.just(optionalReport.get());
                    } else {
                        return Mono.empty();
                    }
                });
    }

    public Mono<EarningsPresentationReportEntity> getEarningsPresentationReport(String stockTicker) {
        LOGGER.info("Looking for latest EarningsPresentationReport with ticker={}", stockTicker);
        return Mono.fromCallable(() -> earningsPresentationReportRepository.findFirstByStockTickerOrderByCreatedAtDesc(stockTicker))
                .flatMap(optionalReport -> {
                    if (optionalReport.isPresent()) {
                        return Mono.just(optionalReport.get());
                    } else {
                        return Mono.empty();
                    }
                });
    }

    public Mono<List<EarningsPresentationReportEntity>> getAllEarningsPresentationReports(String stockTicker) {
        LOGGER.info("Looking for all EarningsPresentationReports with ticker={}", stockTicker);
        return Mono.fromCallable(() -> earningsPresentationReportRepository
                .findByStockTickerAndStatusOrderByCreatedAtDesc(stockTicker, JobStatus.COMPLETED.toString()))
                .map(reports -> reports.stream()
                        .filter(r -> r.getReport() != null && r.getReport().companyMetadata() != null)
                        .sorted((a, b) -> {
                            String periodA = a.getReport().companyMetadata().reportPeriod();
                            String periodB = b.getReport().companyMetadata().reportPeriod();
                            return periodB.compareTo(periodA);
                        })
                        .toList());
    }

    public Mono<JobResponse> triggerNewResearch(String stockTicker) {
        try {
            String discoveryPrompt = StreamUtils.copyToString(discoveryPromptResource.getInputStream(), StandardCharsets.UTF_8);
            String synthesisPrompt = StreamUtils.copyToString(synthesisPromptResource.getInputStream(), StandardCharsets.UTF_8);
            String compileReportPrompt = StreamUtils.copyToString(compileReportPromptResource.getInputStream(), StandardCharsets.UTF_8);

            String subject = String.format("%s stock news and developments from the last month", stockTicker.toUpperCase());
            ResearchRequest request = new ResearchRequest(
                    ResearchTopic.NEWS,
                    subject,
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
                        NewsReportEntity report = new NewsReportEntity();
                        report.setJobId(response.jobId());
                        report.setStockTicker(stockTicker.toUpperCase());
                        report.setTopic(response.topic());
                        report.setStatus(response.status().toString());
                        report.setCreatedAt(LocalDateTime.now());
                        newsReportRepository.save(report);
                        trackJob(response.jobId(), stockTicker.toUpperCase(), response.topic());
                        LOGGER.info("Saved initial news report record for jobId: {}", response.jobId());
                    })
                    .doOnError(error -> LOGGER.error("Error creating research job: {}", error.getMessage()));
        } catch (IOException e) {
            LOGGER.error("Error loading prompts: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    public Mono<JobStatusResponse> getJobStatus(String jobId) {
        return Mono.fromCallable(() -> researchJobRecordRepository.findById(jobId))
                .flatMap(optionalRecord -> {
                    if (optionalRecord.isPresent()) {
                        ResearchJobRecord record = optionalRecord.get();
                        String statusStr = record.getStatus();
                        if (JobStatus.COMPLETED.toString().equals(statusStr)) {
                            return fetchCompletedReportFromCollection(record);
                        } else if (JobStatus.FAILED.toString().equals(statusStr)) {
                            return Mono.just(new JobStatusResponse(statusStr, null, null));
                        } else {
                            return fetchStatusFromExternalService(jobId, record);
                        }
                    } else {
                        return Mono.empty();
                    }
                });
    }

    private Mono<JobStatusResponse> fetchCompletedReportFromCollection(ResearchJobRecord record) {
        ResearchTopic topic = record.getTopic();
        if (topic == ResearchTopic.NEWS) {
            return Mono.fromCallable(() -> newsReportRepository.findById(record.getJobId()))
                    .flatMap(optionalReport -> {
                        if (optionalReport.isPresent()) {
                            NewsReportEntity report = optionalReport.get();
                            return Mono.just(new JobStatusResponse(record.getStatus(), report.getReport(), null));
                        }
                        return Mono.just(new JobStatusResponse(record.getStatus(), null, null));
                    });
        } else if (topic == ResearchTopic.EARNINGS_PRESENTATION) {
            return Mono.fromCallable(() -> earningsPresentationReportRepository.findById(record.getJobId()))
                    .flatMap(optionalReport -> {
                        if (optionalReport.isPresent()) {
                            EarningsPresentationReportEntity report = optionalReport.get();
                            return Mono.just(new JobStatusResponse(record.getStatus(), report.getReport(), null));
                        }
                        return Mono.just(new JobStatusResponse(record.getStatus(), null, null));
                    });
        }
        return Mono.just(new JobStatusResponse(record.getStatus(), null, null));
    }

    private Mono<JobStatusResponse> fetchStatusFromExternalService(String jobId, ResearchJobRecord existingRecord) {
        LOGGER.info("Fetching research status for jobId: {} from external service", jobId);
        return webClient.get()
                .uri("/api/research/{jobId}", jobId)
                .retrieve()
                .bodyToMono(JobStatusResponse.class)
                .doOnNext(response -> {
                    if (JobStatus.COMPLETED.toString().equals(response.status())) {
                        saveCompletedReport(jobId, response, existingRecord);
                    } else if (JobStatus.FAILED.toString().equals(response.status())) {
                        updateFailedReport(jobId, response, existingRecord);
                    }
                })
                .doOnError(error -> LOGGER.error("Error getting research status for jobId {}: {}", jobId, error.getMessage()));
    }

    private void saveCompletedReport(String jobId, JobStatusResponse response, ResearchJobRecord existingRecord) {
        if (existingRecord != null) {
            existingRecord.setStatus(response.status().toString());
            researchJobRecordRepository.save(existingRecord);
        }

        ReportResult reportResult = response.result();
        if (reportResult != null && existingRecord != null) {
            ResearchTopic topic = existingRecord.getTopic();
            if (topic == ResearchTopic.NEWS) {
                NewsReportEntity newsReport = new NewsReportEntity();
                newsReport.setJobId(jobId);
                newsReport.setStockTicker(existingRecord.getStockTicker());
                newsReport.setTopic(topic);
                newsReport.setStatus(response.status().toString());
                newsReport.setReport((NewsReport) reportResult);
                newsReport.setCreatedAt(LocalDateTime.now());
                newsReportRepository.save(newsReport);
            } else if (topic == ResearchTopic.EARNINGS_PRESENTATION) {
                EarningsPresentationReportEntity earningsReport = new EarningsPresentationReportEntity();
                earningsReport.setJobId(jobId);
                earningsReport.setStockTicker(existingRecord.getStockTicker());
                earningsReport.setTopic(topic);
                earningsReport.setStatus(response.status().toString());
                earningsReport.setReport((EarningsPresentationReport) reportResult);
                earningsReport.setCreatedAt(LocalDateTime.now());
                earningsPresentationReportRepository.save(earningsReport);
            }
        }
        LOGGER.info("Saved completed research report for jobId: {}", jobId);
    }

    private void updateFailedReport(String jobId, JobStatusResponse response, ResearchJobRecord existingRecord) {
        if (existingRecord != null) {
            existingRecord.setStatus(response.status().toString());
            researchJobRecordRepository.save(existingRecord);
            LOGGER.info("Updated status to FAILED for jobId: {}", jobId);
        }
    }

    public Mono<JobResponse> processDocument(byte[] pdfBytes, String stockTicker) {
        try {
            String perDocumentPrompt = StreamUtils.copyToString(perDocumentPromptResource.getInputStream(), StandardCharsets.UTF_8);
            String perPagePrompt = StreamUtils.copyToString(perPagePromptResource.getInputStream(), StandardCharsets.UTF_8);

            var request = new ResearchDocumentRequest(ResearchTopic.EARNINGS_PRESENTATION, perPagePrompt, perDocumentPrompt);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("request", request, MediaType.APPLICATION_JSON);

            ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
                @Override
                public String getFilename() {
                    return stockTicker.toUpperCase() + ".pdf";
                }
            };
            bodyBuilder.part("pdf", resource, MediaType.APPLICATION_PDF);

            return webClient.post()
                    .uri("/api/research/document")
                    .contentType(new org.springframework.http.MediaType("multipart", "form-data"))
                    .bodyValue(bodyBuilder.build())
                    .retrieve()
                    .bodyToMono(JobResponse.class)
                    .doOnNext(response -> {
                        EarningsPresentationReportEntity report = new EarningsPresentationReportEntity();
                        report.setJobId(response.jobId());
                        report.setStockTicker(stockTicker.toUpperCase());
                        report.setTopic(response.topic());
                        report.setStatus(response.status().toString());
                        report.setCreatedAt(LocalDateTime.now());
                        earningsPresentationReportRepository.save(report);
                        trackJob(response.jobId(), stockTicker.toUpperCase(), response.topic());
                        LOGGER.info("Saved initial earnings presentation report record for jobId: {} from document", response.jobId());
                    })
                    .doOnError(error -> LOGGER.error("Error processing document: {}", error.getMessage()));
        } catch (IOException e) {
            LOGGER.error("Error loading prompts: {}", e.getMessage());
            return Mono.error(e);
        }
    }
}