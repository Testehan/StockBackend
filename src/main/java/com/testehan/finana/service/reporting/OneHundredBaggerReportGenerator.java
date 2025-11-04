package com.testehan.finana.service.reporting;

import com.testehan.finana.model.ChecklistReport;
import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.ReportType;
import com.testehan.finana.service.reporting.calc.ReportItemCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
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
public class OneHundredBaggerReportGenerator implements ReportGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OneHundredBaggerReportGenerator.class);

    private final List<ReportItemCalculator> oneHundredBaggerCalculators;
    private final Executor checklistExecutor;
    private final ChecklistSseService checklistSseService;
    private final ChecklistReportPersistenceService checklistReportPersistenceService;

    public OneHundredBaggerReportGenerator(List<ReportItemCalculator> oneHundredBaggerCalculators,
                                           @Qualifier("checklistExecutor") Executor checklistExecutor,
                                           ChecklistSseService checklistSseService,
                                           ChecklistReportPersistenceService checklistReportPersistenceService) {
        this.oneHundredBaggerCalculators = oneHundredBaggerCalculators;
        this.checklistExecutor = checklistExecutor;
        this.checklistSseService = checklistSseService;
        this.checklistReportPersistenceService = checklistReportPersistenceService;
    }

    @Override
    public void generate(String ticker, ReportType reportType, SseEmitter sseEmitter) throws InterruptedException, ExecutionException, IOException {
        List<CompletableFuture<Collection<ReportItem>>> futures = new ArrayList<>();
        for (ReportItemCalculator calculator : oneHundredBaggerCalculators) {
            CompletableFuture<Collection<ReportItem>> future = CompletableFuture.supplyAsync(() ->
                    calculator.calculate(ticker, reportType, sseEmitter), checklistExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<ReportItem> checklistReportItems = new ArrayList<>();
        for (CompletableFuture<Collection<ReportItem>> future : futures) {
            checklistReportItems.addAll(future.get());
        }

        checklistSseService.sendSseEvent(sseEmitter, "Building and saving Checklist report...");
        ChecklistReport checklistReport = checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, getReportType());
        checklistSseService.sendSseEvent(sseEmitter, "Checklist report built and saved.");

        sseEmitter.send(SseEmitter.event()
                .name("COMPLETED")
                .data(checklistReport, MediaType.APPLICATION_JSON));

        sseEmitter.complete();
        LOGGER.info("Checklist report generation complete for {}", ticker);
    }

    @Override
    public ReportType getReportType() {
        return ReportType.ONE_HUNDRED_BAGGER;
    }
}
