package com.testehan.finana.service.reporting;

import com.testehan.finana.model.FerolReport;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.GeneratedReport;
import com.testehan.finana.repository.GeneratedReportRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FerolReportPersistenceService {
    private final GeneratedReportRepository generatedReportRepository;

    public FerolReportPersistenceService(GeneratedReportRepository generatedReportRepository) {
        this.generatedReportRepository = generatedReportRepository;
    }

    public FerolReport buildAndSaveReport(String ticker, List<FerolReportItem> ferolReportItems) {
        GeneratedReport generatedReport = generatedReportRepository.findBySymbol(ticker).orElse(new GeneratedReport());
        if (generatedReport.getSymbol()==null) {
            generatedReport.setSymbol(ticker);
        }

        FerolReport ferolReport = generatedReport.getFerolReport();
        if (ferolReport == null) {
            ferolReport = new FerolReport();
        }
        ferolReport.setItems(ferolReportItems);
        ferolReport.setGeneratedAt(LocalDateTime.now());
        generatedReport.setFerolReport(ferolReport);

        generatedReportRepository.save(generatedReport);

        return ferolReport;
    }
}
