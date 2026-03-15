package com.testehan.finana.service.reporting;

import com.testehan.finana.model.reporting.ChecklistReport;
import com.testehan.finana.model.reporting.GeneratedReport;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.reporting.ReportType;
import com.testehan.finana.model.reporting.UserReportOverride;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.repository.UserReportOverrideRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChecklistReportPersistenceService {
    private final GeneratedReportRepository generatedReportRepository;
    private final UserReportOverrideRepository userReportOverrideRepository;

    public ChecklistReportPersistenceService(GeneratedReportRepository generatedReportRepository,
                                             UserReportOverrideRepository userReportOverrideRepository) {
        this.generatedReportRepository = generatedReportRepository;
        this.userReportOverrideRepository = userReportOverrideRepository;
    }

    public ChecklistReport buildAndSaveReport(String ticker, List<ReportItem> checklistReportItems, ReportType reportType, LocalDateTime dateTime) {
        GeneratedReport generatedReport = generatedReportRepository.findBySymbol(ticker).orElse(new GeneratedReport());
        if (generatedReport.getSymbol()==null) {
            generatedReport.setSymbol(ticker);
        }

        ChecklistReport checklistReport = new ChecklistReport();
        checklistReport.setItems(checklistReportItems);

        switch (reportType) {
            case FEROL -> {
                generatedReport.setFerolReport(checklistReport);
                generatedReport.setFerolReportGeneratedAt(LocalDateTime.now());
                generatedReport.setTotalFerolScore(checklistReport.getItems().stream()
                        .mapToInt(item -> item.getScore() != null ? item.getScore() : 0)
                        .sum());
            }
            case ONE_HUNDRED_BAGGER -> {
                generatedReport.setOneHundredBaggerReport(checklistReport);
                generatedReport.setOneHundredBaggerReportGeneratedAt(LocalDateTime.now());
                generatedReport.setTotalOneHundredBaggerScore(checklistReport.getItems().stream()
                        .mapToInt(item -> item.getScore() != null ? item.getScore() : 0)
                        .sum());
            }
        }

        generatedReportRepository.save(generatedReport);

        List<UserReportOverride> existingOverrides = userReportOverrideRepository.findBySymbolAndReportType(ticker, reportType);
        existingOverrides.forEach(o -> o.setNeedsReview(true));
        userReportOverrideRepository.saveAll(existingOverrides);

        return checklistReport;
    }
}
