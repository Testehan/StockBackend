package com.testehan.finana.service.reporting;

import com.testehan.finana.model.ChecklistReport;
import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.GeneratedReport;
import com.testehan.finana.repository.GeneratedReportRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChecklistReportPersistenceService {
    private final GeneratedReportRepository generatedReportRepository;

    public ChecklistReportPersistenceService(GeneratedReportRepository generatedReportRepository) {
        this.generatedReportRepository = generatedReportRepository;
    }

    public ChecklistReport buildAndSaveReport(String ticker, List<ReportItem> checklistReportItems, String reportType) {
        GeneratedReport generatedReport = generatedReportRepository.findBySymbol(ticker).orElse(new GeneratedReport());
        if (generatedReport.getSymbol()==null) {
            generatedReport.setSymbol(ticker);
        }

        ChecklistReport checklistReport = new ChecklistReport();
        checklistReport.setItems(checklistReportItems);
        checklistReport.setGeneratedAt(LocalDateTime.now());

        if (reportType.equalsIgnoreCase("ferol")) {
            generatedReport.setFerolReport(checklistReport);
        } else if (reportType.equalsIgnoreCase("100bagger")) {
            generatedReport.setOneHundredBaggerReport(checklistReport);
        }

        generatedReportRepository.save(generatedReport);

        return checklistReport;
    }
}
