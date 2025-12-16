package com.testehan.finana.service.reporting;

import com.testehan.finana.model.reporting.ChecklistReport;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.reporting.ReportType;
import com.testehan.finana.service.reporting.calc.ReportItemCalculator;
import com.testehan.finana.service.reporting.events.CompletionEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class FerolReportGenerator implements ReportGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(FerolReportGenerator.class);

    private final List<ReportItemCalculator> ferolCalculators;
    private final ChecklistReportPersistenceService checklistReportPersistenceService;
    private final ApplicationEventPublisher eventPublisher;

    public FerolReportGenerator(List<ReportItemCalculator> ferolCalculators,
                                ChecklistReportPersistenceService checklistReportPersistenceService,
                                ApplicationEventPublisher eventPublisher) {
        this.ferolCalculators = ferolCalculators;
        this.checklistReportPersistenceService = checklistReportPersistenceService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void generate(String ticker, ReportType reportType, SseEmitter sseEmitter) throws InterruptedException {
        List<ReportItem> checklistReportItems = new ArrayList<>();

        for (ReportItemCalculator calculator : ferolCalculators) {
            Collection<ReportItem> result = calculator.calculate(ticker, reportType, sseEmitter);
            checklistReportItems.addAll(result);
// todo this is hopefully temporary as i got erros from gemini llm about overusage ...other people complain about this as well
            try {
                Thread.sleep(10000);
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
        return ReportType.FEROL;
    }
}
