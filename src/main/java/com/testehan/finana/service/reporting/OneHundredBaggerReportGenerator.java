package com.testehan.finana.service.reporting;

import com.testehan.finana.model.reporting.ChecklistReport;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.reporting.ReportType;
import com.testehan.finana.service.reporting.calc.ReportItemCalculator;
import com.testehan.finana.service.reporting.events.CompletionEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@Service
public class OneHundredBaggerReportGenerator implements ReportGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OneHundredBaggerReportGenerator.class);

    @Value("${app.llm.sequential-delay-ms:0}")
    private long sequentialDelayMs;

    private final List<ReportItemCalculator> oneHundredBaggerCalculators;
    private final ChecklistReportPersistenceService checklistReportPersistenceService;
    private final ApplicationEventPublisher eventPublisher;

    public OneHundredBaggerReportGenerator(List<ReportItemCalculator> oneHundredBaggerCalculators,
                                           ChecklistReportPersistenceService checklistReportPersistenceService,
                                           ApplicationEventPublisher eventPublisher) {
        this.oneHundredBaggerCalculators = oneHundredBaggerCalculators;
        this.checklistReportPersistenceService = checklistReportPersistenceService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void generate(String ticker, ReportType reportType, SseEmitter sseEmitter) throws InterruptedException {
        List<ReportItem> checklistReportItems = new ArrayList<>();
        Executor contextExecutor = new DelegatingSecurityContextExecutor(ForkJoinPool.commonPool());

        List<ReportItemCalculator> parallelCalculators = oneHundredBaggerCalculators.stream()
                .filter(ReportItemCalculator::canRunInParallel)
                .collect(Collectors.toList());
        List<ReportItemCalculator> sequentialCalculators = oneHundredBaggerCalculators.stream()
                .filter(c -> !c.canRunInParallel())
                .collect(Collectors.toList());

        List<CompletableFuture<Collection<ReportItem>>> parallelFutures = parallelCalculators.stream()
                .map(calculator -> CompletableFuture.supplyAsync(
                        () -> calculator.calculate(ticker, reportType, sseEmitter),
                        contextExecutor))
                .collect(Collectors.toList());

        for (CompletableFuture<Collection<ReportItem>> future : parallelFutures) {
            checklistReportItems.addAll(future.join());
        }

        for (ReportItemCalculator calculator : sequentialCalculators) {
            Collection<ReportItem> result = calculator.calculate(ticker, reportType, sseEmitter);
            checklistReportItems.addAll(result);
            try {
                Thread.sleep(sequentialDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Building and saving Checklist report..."));
        var reportDate = LocalDateTime.now();
        ChecklistReport checklistReport = checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, getReportType(), reportDate);
        checklistReport.setGeneratedAt(reportDate);
        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Checklist report built and saved."));

        eventPublisher.publishEvent(new CompletionEvent(this, ticker, sseEmitter, checklistReport));
        LOGGER.info("Checklist report generation complete for {}", ticker);
    }

    @Override
    public ReportType getReportType() {
        return ReportType.ONE_HUNDRED_BAGGER;
    }

}