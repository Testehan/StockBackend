package com.testehan.finana.service.reporting;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.service.CompanyDataService;
import com.testehan.finana.service.FinancialDataOrchestrator;
import com.testehan.finana.service.reporting.events.CompletionEvent;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChecklistReportOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChecklistReportOrchestrator.class);

    private final FinancialDataOrchestrator financialDataOrchestrator;
    private final CompanyDataService companyDataService;
    private final ChecklistReportPersistenceService checklistReportPersistenceService;
    private final GeneratedReportRepository generatedReportRepository;
    private final Executor checklistExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<ReportType, ReportGenerator> reportGenerators;

    public ChecklistReportOrchestrator(FinancialDataOrchestrator financialDataOrchestrator,
                                       CompanyDataService companyDataService,
                                       ChecklistReportPersistenceService checklistReportPersistenceService,
                                       GeneratedReportRepository generatedReportRepository,
                                       @Qualifier("checklistExecutor") Executor checklistExecutor,
                                       ApplicationEventPublisher eventPublisher,
                                       List<ReportGenerator> reportGenerators) {
        this.financialDataOrchestrator = financialDataOrchestrator;
        this.companyDataService = companyDataService;
        this.checklistReportPersistenceService = checklistReportPersistenceService;
        this.generatedReportRepository = generatedReportRepository;
        this.checklistExecutor = checklistExecutor;
        this.eventPublisher = eventPublisher;
        this.reportGenerators = reportGenerators.stream()
                .collect(Collectors.toMap(ReportGenerator::getReportType, Function.identity()));
    }

    public SseEmitter getChecklistReport(String ticker, boolean recreateReport, ReportType reportType) {
        SseEmitter sseEmitter = new SseEmitter(3600000L); // Timeout set to 1 hour

        ReportGenerator generator = reportGenerators.get(reportType);
        if (generator == null) {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Invalid report type."));
            sseEmitter.complete();
            return sseEmitter;
        }

        getOrGenerateChecklistReport(ticker, recreateReport, reportType, sseEmitter);
        return sseEmitter;
    }

    private void getOrGenerateChecklistReport(String ticker, boolean recreateReport, ReportType reportType, SseEmitter sseEmitter) {
        checklistExecutor.execute(() -> {
            try {
                if (!recreateReport) {
                    eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Attempting to load report from database..."));
                    Optional<GeneratedReport> existingGeneratedReport = generatedReportRepository.findBySymbol(ticker);
                    if (existingGeneratedReport.isPresent()) {
                        ChecklistReport checklistReport = getReportFromGeneratedReport(existingGeneratedReport.get(), reportType);
                        if (Objects.nonNull(checklistReport)) {
                            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Report loaded from database."));
                            eventPublisher.publishEvent(new CompletionEvent(this, ticker, sseEmitter, checklistReport));
                            LOGGER.info("Checklist report for {} loaded from DB and sent.", ticker);
                            return; // Exit as report is sent
                        }
                    }
                    eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Report not found in database or incomplete. You must generate a new report."));
                    sseEmitter.complete();
                } else {
                    eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Initiating Checklist report generation for " + ticker + "..."));
                    generateReport(ticker, reportType, sseEmitter);
                }
            } catch (Exception e) {
                eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, e));
            }
        });
    }

    private void generateReport(String ticker, ReportType reportType, SseEmitter sseEmitter) throws InterruptedException, ExecutionException, IOException {
        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Ensuring financial data is present..."));
        financialDataOrchestrator.ensureFinancialDataIsPresent(ticker);
        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Financial data check complete."));

        ReportGenerator generator = reportGenerators.get(reportType);
        generator.generate(ticker, reportType, sseEmitter);
    }

    public ChecklistReport saveChecklistReport(String ticker, List<ReportItem> checklistReportItems, ReportType reportType) {
        LOGGER.info("Saving Checklist report for {}", ticker);
        return checklistReportPersistenceService.buildAndSaveReport(ticker, checklistReportItems, reportType);
    }

    public List<ChecklistReportSummaryDTO> getChecklistReportsSummary() {
        List<CompanyOverview> allCompanies = companyDataService.findAllCompanyOverview();
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


        

    