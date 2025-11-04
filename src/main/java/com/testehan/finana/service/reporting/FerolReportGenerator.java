package com.testehan.finana.service.reporting;

import com.testehan.finana.model.ChecklistReport;
import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.ReportType;
import com.testehan.finana.service.reporting.calc.ReportItemCalculator;
import com.testehan.finana.service.reporting.events.CompletionEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@Service
public class FerolReportGenerator implements ReportGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(FerolReportGenerator.class);

    private final List<ReportItemCalculator> ferolCalculators;
    private final Executor checklistExecutor;
    private final ChecklistReportPersistenceService checklistReportPersistenceService;
    private final ApplicationEventPublisher eventPublisher;

    public FerolReportGenerator(List<ReportItemCalculator> ferolCalculators,
                                @Qualifier("checklistExecutor") Executor checklistExecutor,
                                ChecklistReportPersistenceService checklistReportPersistenceService,
                                ApplicationEventPublisher eventPublisher) {
        this.ferolCalculators = ferolCalculators;
        this.checklistExecutor = checklistExecutor;
        this.checklistReportPersistenceService = checklistReportPersistenceService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void generate(String ticker, ReportType reportType, SseEmitter sseEmitter) throws InterruptedException, ExecutionException, IOException {
        List<CompletableFuture<Collection<ReportItem>>> futures = new ArrayList<>();
        for (ReportItemCalculator calculator : ferolCalculators) {
            CompletableFuture<Collection<ReportItem>> future = CompletableFuture.supplyAsync(() ->
                    calculator.calculate(ticker, reportType, sseEmitter), checklistExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<ReportItem> checklistReportItems = new ArrayList<>();
        for (CompletableFuture<Collection<ReportItem>> future : futures) {
            checklistReportItems.addAll(future.get());
        }

        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Building and saving Checklist report..."));
        ChecklistReport checklistReport = checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, getReportType());
        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Checklist report built and saved."));

        eventPublisher.publishEvent(new CompletionEvent(this, ticker, sseEmitter, checklistReport));
        LOGGER.info("Checklist report generation complete for {}", ticker);
    }

    @Override
    public ReportType getReportType() {
        return ReportType.FEROL;
    }
}
