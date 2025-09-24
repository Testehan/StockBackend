package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.FinancialRatiosData;
import com.testehan.finana.model.FinancialRatiosReport;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.service.reporting.FerolSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RoicCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoicCalculator.class);

    private final FinancialRatiosRepository financialRatiosRepository;
    private final FerolSseService ferolSseService;

    public RoicCalculator(FinancialRatiosRepository financialRatiosRepository, FerolSseService ferolSseService) {
        this.financialRatiosRepository = financialRatiosRepository;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        ferolSseService.sendSseEvent(sseEmitter, "Calculating Return on Invested Capital (ROIC)...");
        Optional<FinancialRatiosData> financialRatiosData = financialRatiosRepository.findBySymbol(ticker);

        if (financialRatiosData.isEmpty() || financialRatiosData.get().getAnnualReports().isEmpty() || financialRatiosData.get().getQuarterlyReports().isEmpty()) {
            LOGGER.warn("No financial ratios data found for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "ROIC calculation skipped: No data found.");
            return new FerolReportItem("roic", 0, "No annual or quarterly financial ratios data available.");
        }

        // Get annual ROIC for 5-year median
        List<BigDecimal> annualRoicValues = financialRatiosData.get().getAnnualReports().stream()
                .filter(report -> report.getRoic() != null)
                .sorted(Comparator.comparing(FinancialRatiosReport::getDate).reversed())
                .limit(5)
                .map(FinancialRatiosReport::getRoic)
                .collect(Collectors.toList());

        if (annualRoicValues.isEmpty()) {
            ferolSseService.sendSseEvent(sseEmitter, "ROIC calculation skipped: No annual ROIC data found.");
            return new FerolReportItem("roic", 0, "No annual ROIC data available for median calculation.");
        }

        // Calculate 5-year median ROIC
        // Sort the list to find median
        Collections.sort(annualRoicValues);
        BigDecimal medianRoic;
        int middle = annualRoicValues.size() / 2;
        if (annualRoicValues.size() % 2 == 1) {
            medianRoic = annualRoicValues.get(middle);
        } else {
            medianRoic = (annualRoicValues.get(middle - 1).add(annualRoicValues.get(middle))).divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);
        }
        ferolSseService.sendSseEvent(sseEmitter, "5-Year Median ROIC: " + medianRoic.toPlainString() + "%");


        // Get latest TTM ROIC (assuming the latest quarterly report's ROIC represents TTM or is close enough)
        // For accurate TTM, one would sum last 4 quarters' net income and divide by average invested capital,
        // but given the `roic` field in `FinancialRatiosReport` is already a single value, we'll use the latest.
        BigDecimal currentTtmRoic = BigDecimal.ZERO;
        Optional<FinancialRatiosReport> latestQuarterlyReport = financialRatiosData.get().getQuarterlyReports().stream()
                .filter(report -> report.getRoic() != null)
                .max(Comparator.comparing(FinancialRatiosReport::getDate));

        if (latestQuarterlyReport.isPresent()) {
            currentTtmRoic = latestQuarterlyReport.get().getRoic();
            ferolSseService.sendSseEvent(sseEmitter, "Latest TTM ROIC: " + currentTtmRoic.toPlainString() + "%");
        } else {
            ferolSseService.sendSseEvent(sseEmitter, "ROIC calculation partially skipped: No latest quarterly ROIC data for TTM.");
            return new FerolReportItem("roic", 0, "No latest quarterly ROIC data for TTM calculation.");
        }


        int score = 0;
        String explanation;
        BigDecimal roicPercentage = currentTtmRoic.multiply(BigDecimal.valueOf(100)); // Convert to percentage for comparison

        if (roicPercentage.compareTo(BigDecimal.valueOf(8)) < 0) { // ROIC < 8%
            score = 0;
            explanation = "ROIC is less than 8% (" + roicPercentage.toPlainString() + "%), indicating the company might be destroying value.";
        } else if (roicPercentage.compareTo(BigDecimal.valueOf(12)) < 0) { // ROIC 8% - 12%
            score = 1;
            explanation = "ROIC is between 8% and 12% (" + roicPercentage.toPlainString() + "%), indicating the company is essentially breaking even on its capital costs.";
        } else if (roicPercentage.compareTo(BigDecimal.valueOf(20)) < 0) { // ROIC 12% - 20%
            score = 2;
            explanation = "ROIC is between 12% and 20% (" + roicPercentage.toPlainString() + "%), indicating a solid compounder.";
        } else { // ROIC > 20%
            score = 3;
            explanation = "ROIC is greater than 20% (" + roicPercentage.toPlainString() + "%), indicating a strong competitive advantage.";
        }

        // Apply "Rising Rule"
        BigDecimal marginOfSafety = BigDecimal.valueOf(0.01); // 1% margin of safety
        BigDecimal medianRoicDecimal = medianRoic.divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP); // Convert median to decimal for comparison
        if (currentTtmRoic.compareTo(medianRoicDecimal.add(marginOfSafety)) > 0 && score < 3) {
            score++;
            explanation += " Additionally, ROIC is rising, suggesting an improving trend.";
        }
        ferolSseService.sendSseEvent(sseEmitter, "ROIC calculation complete. Score: " + score);

        return new FerolReportItem("roic", score, explanation);
    }
}
