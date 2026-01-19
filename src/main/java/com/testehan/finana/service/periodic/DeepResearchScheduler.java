package com.testehan.finana.service.periodic;

import com.testehan.deepresearch.model.JobResponse;
import com.testehan.deepresearch.model.ResearchJob.JobStatus;
import com.testehan.finana.model.reporting.NewsReportEntity;
import com.testehan.finana.model.research.ResearchJobRecord;
import com.testehan.finana.model.user.UserStock;
import com.testehan.finana.model.user.UserStockStatus;
import com.testehan.finana.repository.NewsReportRepository;
import com.testehan.finana.repository.UserStockRepository;
import com.testehan.finana.service.DeepResearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeepResearchScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepResearchScheduler.class);
    // todo USER_ID needs to change to logged in user
    private static final String USER_ID = "dante";
    private final UserStockRepository userStockRepository;
    private final NewsReportRepository newsReportRepository;
    private final DeepResearchService deepResearchService;

    private final List<String> tickersToTrack = List.of(
            "NET"
    );

    public DeepResearchScheduler(UserStockRepository userStockRepository,
                                 NewsReportRepository newsReportRepository,
                                 DeepResearchService deepResearchService) {
        this.userStockRepository = userStockRepository;
        this.newsReportRepository = newsReportRepository;
        this.deepResearchService = deepResearchService;
    }

    //   @Scheduled(fixedRate = 86_400_000) // every 24 hours
    public void checkAndTriggerResearch() {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusDays(30);

        // TODO Right now i am just using NET for testing purposes..
        // loadRelevantStockTickers();

        for (String ticker : tickersToTrack) {
            List<NewsReportEntity> existingReports = newsReportRepository
                    .findByStockTickerAndCreatedAtAfterOrderByCreatedAtDesc(ticker, oneMonthAgo);

            if (existingReports.isEmpty()) {
                LOGGER.info("No recent report found for {}. Triggering new research...", ticker);
                triggerResearch(ticker);
            } else {
                NewsReportEntity latestReport = existingReports.get(0);
                if (!JobStatus.COMPLETED.toString().equals(latestReport.getStatus())) {
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
        List<ResearchJobRecord> runningJobs = deepResearchService.getRunningJobs();
        if (runningJobs.isEmpty()) {
            return;
        }

        LOGGER.debug("Polling {} running jobs...", runningJobs.size());

        for (ResearchJobRecord record : runningJobs) {
            String jobId = record.getJobId();

            deepResearchService.getJobStatus(jobId)
                    .subscribe(
                            response -> {
                                LOGGER.debug("Status for jobId {}: {}", jobId, response.status());
                                String statusStr = response.status().toString();
                                if (JobStatus.COMPLETED.toString().equals(statusStr) || JobStatus.FAILED.toString().equals(statusStr)) {
                                    LOGGER.info("Job {} finished with status {}.", jobId, response.status());
                                }
                            },
                            error -> LOGGER.error("Error polling job {}: {}", jobId, error.getMessage())
                    );
        }
    }

    private void triggerResearch(String ticker) {
        try {
            Mono<JobResponse> responseMono = deepResearchService.triggerNewResearch(ticker);
            responseMono.subscribe(
                    response -> {
                        LOGGER.info("Triggered research for {}. JobId: {}", ticker, response.jobId());
                    },
                    error -> LOGGER.error("Error triggering research for {}: {}", ticker, error.getMessage())
            );
        } catch (Exception e) {
            LOGGER.error("Exception triggering research for {}: {}", ticker, e.getMessage());
        }
    }

    private void loadRelevantStockTickers() {
        LOGGER.info("Starting deep research for user: {}", USER_ID);

        List<UserStock> relevantStocks = userStockRepository.findByUserId(USER_ID).stream()
                .filter(stock -> stock.getStatus() == UserStockStatus.OWNED ||
                        stock.getStatus() == UserStockStatus.BUY_CANDIDATE)
                .toList();

        if (relevantStocks.isEmpty()) {
            LOGGER.info("No stocks with status OWNED or BUY_CANDIDATE found for user: {}", USER_ID);
            return;
        }

        LOGGER.info("Found {} stocks for deep research for user {}:", relevantStocks.size(), USER_ID);

        for (UserStock stock : relevantStocks) {
            String ticker = stock.getStockId();
            tickersToTrack.add(ticker);
        }
    }
}