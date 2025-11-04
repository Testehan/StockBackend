package com.testehan.finana.service.reporting;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.reporting.calc.ReportItemCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class ChecklistReportOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChecklistReportOrchestrator.class);

    private final FinancialDataService financialDataService;
    private final ChecklistSseService checklistSseService;
    private final ChecklistReportPersistenceService checklistReportPersistenceService;
    private final GeneratedReportRepository generatedReportRepository;
    private final Executor checklistExecutor;

    private final List<ReportItemCalculator> ferolCalculators;
    private final List<ReportItemCalculator> oneHundredBaggerCalculators;


    public ChecklistReportOrchestrator(FinancialDataService financialDataService,
                                       ChecklistSseService checklistSseService,
                                       ChecklistReportPersistenceService checklistReportPersistenceService,
                                       GeneratedReportRepository generatedReportRepository,
                                       @Qualifier("checklistExecutor") Executor checklistExecutor,
                                       List<ReportItemCalculator> ferolCalculators,
                                       List<ReportItemCalculator> oneHundredBaggerCalculators) {
        this.financialDataService = financialDataService;
        this.checklistSseService = checklistSseService;
        this.checklistReportPersistenceService = checklistReportPersistenceService;
        this.generatedReportRepository = generatedReportRepository;
        this.checklistExecutor = checklistExecutor;
        this.ferolCalculators = ferolCalculators;
        this.oneHundredBaggerCalculators = oneHundredBaggerCalculators;
    }


    public SseEmitter getChecklistReport(String ticker, boolean recreateReport, ReportType reportType) {
        SseEmitter sseEmitter = new SseEmitter(3600000L); // Timeout set to 1 hour

        switch (reportType) {
            case FEROL, ONE_HUNDRED_BAGGER -> getOrGenerateChecklistReport(ticker, recreateReport, reportType, sseEmitter);
            default -> {
                checklistSseService.sendSseEvent(sseEmitter, "Invalid report type.");
                sseEmitter.complete();
            }
        }

        return sseEmitter;
    }

    private void getOrGenerateChecklistReport(String ticker, boolean recreateReport, ReportType reportType, SseEmitter sseEmitter) {
        checklistExecutor.execute(() -> {
            try {
                if (!recreateReport) {
                    checklistSseService.sendSseEvent(sseEmitter, "Attempting to load report from database...");
                    Optional<GeneratedReport> existingGeneratedReport = generatedReportRepository.findBySymbol(ticker);
                    if (existingGeneratedReport.isPresent()) {
                        ChecklistReport checklistReport = getReportFromGeneratedReport(existingGeneratedReport.get(), reportType);
                        if (Objects.nonNull(checklistReport)) {
                            checklistSseService.sendSseEvent(sseEmitter, "Report loaded from database.");
                            sseEmitter.send(SseEmitter.event()
                                    .name("COMPLETED")
                                    .data(checklistReport, MediaType.APPLICATION_JSON));
                            sseEmitter.complete();
                            LOGGER.info("Checklist report for {} loaded from DB and sent.", ticker);
                            return; // Exit as report is sent
                        }
                    }
                    checklistSseService.sendSseEvent(sseEmitter, "Report not found in database or incomplete. You must generate a new report.");
                    sseEmitter.send(SseEmitter.event()
                            .name("COMPLETED"));

                    sseEmitter.complete();
                    return;

                } else {
                    checklistSseService.sendSseEvent(sseEmitter, "Initiating Checklist report generation for " + ticker + "...");
                }

                generateReport(ticker, reportType, sseEmitter);

            } catch (Exception e) {
                LOGGER.error("Error generating Checklist report for {}: {}", ticker, e.getMessage(), e);
                sseEmitter.completeWithError(e);
            }
        });
    }

    private void generateReport(String ticker, ReportType reportType, SseEmitter sseEmitter) throws InterruptedException, ExecutionException, IOException {
        checklistSseService.sendSseEvent(sseEmitter, "Ensuring financial data is present...");
        financialDataService.ensureFinancialDataIsPresent(ticker);
        checklistSseService.sendSseEvent(sseEmitter, "Financial data check complete.");

        switch (reportType) {
            case FEROL -> generateFerolReport(ticker, sseEmitter, reportType);
            case ONE_HUNDRED_BAGGER -> generate100BaggerReport(ticker, sseEmitter, reportType);
        }
    }

    private void generateFerolReport(String ticker, SseEmitter sseEmitter, ReportType reportType) throws InterruptedException, ExecutionException, IOException {
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

        checklistSseService.sendSseEvent(sseEmitter, "Building and saving Checklist report...");
        ChecklistReport checklistReport = checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, ReportType.FEROL);
        checklistSseService.sendSseEvent(sseEmitter, "Checklist report built and saved.");

        sseEmitter.send(SseEmitter.event()
                .name("COMPLETED")
                .data(checklistReport, MediaType.APPLICATION_JSON));

        sseEmitter.complete();
        LOGGER.info("Checklist report generation complete for {}", ticker);
    }

    private void generate100BaggerReport(String ticker, SseEmitter sseEmitter, ReportType reportType) throws InterruptedException, ExecutionException, IOException {
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
        ChecklistReport checklistReport = checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, ReportType.ONE_HUNDRED_BAGGER);
        checklistSseService.sendSseEvent(sseEmitter, "Checklist report built and saved.");

        sseEmitter.send(SseEmitter.event()
                .name("COMPLETED")
                .data(checklistReport, MediaType.APPLICATION_JSON));

        sseEmitter.complete();
        LOGGER.info("Checklist report generation complete for {}", ticker);
    }

    public ChecklistReport saveChecklistReport(String ticker, List<ReportItem> checklistReportItems, ReportType reportType) {
        LOGGER.info("Saving Checklist report for {}", ticker);
        return checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, reportType);
    }

    public List<ChecklistReportSummaryDTO> getChecklistReportsSummary() {
        List<CompanyOverview> allCompanies = financialDataService.findAllCompanyOverview();
        List<GeneratedReport> allGeneratedReports = generatedReportRepository.findAll();

        Map<String, GeneratedReport> reportMap = allGeneratedReports.stream()
                .collect(Collectors.toMap(GeneratedReport::getSymbol, report -> report));

        return allCompanies.stream()
                .map(company -> {
                    String ticker = company.getSymbol();
                    GeneratedReport generatedReport = reportMap.get(ticker);

                    int totalFerolScore = 0;
                    LocalDateTime ferolReportGeneratedAt = null;
                    int totalHundredBaggerScore = 0;
                    LocalDateTime hundredBaggerReportGeneratedAt = null;

                    if (generatedReport != null) {
                        ChecklistReport ferolReport = generatedReport.getFerolReport();
                        if (ferolReport != null && ferolReport.getItems() != null) {
                            ferolReportGeneratedAt = ferolReport.getGeneratedAt();
                            totalFerolScore = ferolReport.getItems().stream()
                                    .mapToInt(item -> item.getScore() != null ? item.getScore() : 0)
                                    .sum();
                        }

                        ChecklistReport hundredBaggerReport = generatedReport.getOneHundredBaggerReport();
                        if (hundredBaggerReport != null && hundredBaggerReport.getItems() != null) {
                            hundredBaggerReportGeneratedAt = hundredBaggerReport.getGeneratedAt();
                            totalHundredBaggerScore = hundredBaggerReport.getItems().stream()
                                    .mapToInt(item -> item.getScore() != null ? item.getScore() : 0)
                                    .sum();
                        }
                    }
                    return new ChecklistReportSummaryDTO(ticker, totalFerolScore, totalHundredBaggerScore, ferolReportGeneratedAt, hundredBaggerReportGeneratedAt);
                })
                .collect(Collectors.toList());
    }

    private ChecklistReport getReportFromGeneratedReport(GeneratedReport generatedReport, ReportType reportType) {
        return switch (reportType) {
            case FEROL -> generatedReport.getFerolReport();
            case ONE_HUNDRED_BAGGER -> generatedReport.getOneHundredBaggerReport();
        };
    }
}

        

    