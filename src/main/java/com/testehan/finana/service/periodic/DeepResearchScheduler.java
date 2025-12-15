package com.testehan.finana.service.periodic;

import com.testehan.finana.model.reporting.DeepResearchReport;
import com.testehan.finana.repository.DeepResearchReportRepository;
import com.testehan.finana.service.DeepResearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeepResearchScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepResearchScheduler.class);

    private final DeepResearchReportRepository deepResearchReportRepository;
    private final DeepResearchService deepResearchService;

    private final Map<String, String> runningJobs = new ConcurrentHashMap<>();

    private final List<String> tickersToTrack = List.of(
            "NET"
    );

    public DeepResearchScheduler(DeepResearchReportRepository deepResearchReportRepository,
                                  DeepResearchService deepResearchService) {
        this.deepResearchReportRepository = deepResearchReportRepository;
        this.deepResearchService = deepResearchService;
        loadRunningJobsFromDb();
    }

    private void loadRunningJobsFromDb() {
        List<DeepResearchReport> runningReports = deepResearchReportRepository.findAllByStatus("RUNNING");
        for (DeepResearchReport report : runningReports) {
            runningJobs.put(report.getJobId(), report.getStockTicker());
        }
        LOGGER.info("Loaded {} running jobs from DB", runningJobs.size());
    }

    @Scheduled(fixedRate = 86_400_000) // every 24 hours
    public void checkAndTriggerResearch() {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusDays(30);

        for (String ticker : tickersToTrack) {
            List<DeepResearchReport> existingReports = deepResearchReportRepository
                    .findByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(ticker, oneMonthAgo);

            if (existingReports.isEmpty()) {
                LOGGER.info("No recent report found for {}. Triggering new research...", ticker);
                triggerResearch(ticker);
            } else {
                DeepResearchReport latestReport = existingReports.get(0);
                if (!"COMPLETED".equals(latestReport.getStatus())) {
                    LOGGER.info("Report for {} has status {}. Re-triggering...", ticker, latestReport.getStatus());
                    triggerResearch(ticker);
                } else {
                    LOGGER.info("Recent report for {} exists with status COMPLETED. Skipping.", ticker);
                }
            }
        }
    }

    @Scheduled(fixedRate = 20_000) // every 20 seconds
    public void pollRunningJobs() {
        if (runningJobs.isEmpty()) {
            return;
        }

        LOGGER.debug("Polling {} running jobs...", runningJobs.size());

        for (Map.Entry<String, String> entry : runningJobs.entrySet()) {
            String jobId = entry.getKey();
            String ticker = entry.getValue();

            deepResearchService.getJobStatus(jobId)
                    .subscribe(
                            response -> {
                                LOGGER.debug("Status for jobId {}: {}", jobId, response.status());
                                if ("COMPLETED".equals(response.status()) || "FAILED".equals(response.status())) {
                                    runningJobs.remove(jobId);
                                    LOGGER.info("Job {} finished with status {}. Removed from running jobs.", jobId, response.status());
                                }
                            },
                            error -> LOGGER.error("Error polling job {}: {}", jobId, error.getMessage())
                    );
        }
    }

    private void triggerResearch(String ticker) {
        try {
            Mono<com.testehan.deepresearch.model.JobResponse> responseMono = deepResearchService.triggerNewResearch(ticker);
            responseMono.subscribe(
                    response -> {
                        LOGGER.info("Triggered research for {}. JobId: {}", ticker, response.jobId());
                        runningJobs.put(response.jobId(), ticker);
                    },
                    error -> LOGGER.error("Error triggering research for {}: {}", ticker, error.getMessage())
            );
        } catch (Exception e) {
            LOGGER.error("Exception triggering research for {}: {}", ticker, e.getMessage());
        }
    }
}