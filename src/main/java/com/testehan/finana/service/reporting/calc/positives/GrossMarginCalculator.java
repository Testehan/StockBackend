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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GrossMarginCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrossMarginCalculator.class);

    private final FinancialRatiosRepository financialRatiosRepository;
    private final FerolSseService ferolSseService;

    public GrossMarginCalculator(FinancialRatiosRepository financialRatiosRepository, FerolSseService ferolSseService) {
        this.financialRatiosRepository = financialRatiosRepository;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        ferolSseService.sendSseEvent(sseEmitter, "Calculating Gross Margin...");
        Optional<FinancialRatiosData> financialRatiosData = financialRatiosRepository.findBySymbol(ticker);

        if (financialRatiosData.isEmpty() || financialRatiosData.get().getQuarterlyReports().isEmpty()) {
            LOGGER.warn("No financial ratios data found for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "Gross Margin calculation skipped: No data found.");
            return new FerolReportItem("grossMargin", 0, "No quarterly financial ratios data available.");
        }

        List<FinancialRatiosReport> quarterlyReports = financialRatiosData.get().getQuarterlyReports().stream()
                .sorted(Comparator.comparing(FinancialRatiosReport::getDate).reversed())
                .limit(4)
                .collect(Collectors.toList());

        if (quarterlyReports.size() < 4) {
            LOGGER.warn("Less than 4 quarterly reports found for gross margin calculation for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "Gross Margin calculation using " + quarterlyReports.size() + " quarters.");
        }

        BigDecimal sumGrossProfitMargin = BigDecimal.ZERO;
        int count = 0;
        for (FinancialRatiosReport report : quarterlyReports) {
            if (report.getGrossProfitMargin() != null) {
                sumGrossProfitMargin = sumGrossProfitMargin.add(report.getGrossProfitMargin());
                count++;
            }
        }

        if (count == 0) {
            ferolSseService.sendSseEvent(sseEmitter, "Gross Margin calculation skipped: No gross profit margin data found in available reports.");
            return new FerolReportItem("grossMargin", 0, "No gross profit margin data available in the last " + quarterlyReports.size() + " quarterly reports.");
        }

        BigDecimal averageGrossProfitMargin = sumGrossProfitMargin.divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_HALF_UP);
        ferolSseService.sendSseEvent(sseEmitter, "Average Gross Margin calculated: " + averageGrossProfitMargin.toPlainString() + "%");

        int score;
        String explanation;

        if (averageGrossProfitMargin.compareTo(BigDecimal.valueOf(0.50)) < 0) { // < 50%
            score = 1;
            explanation = "Average Gross Margin is less than 50% (" + averageGrossProfitMargin.multiply(BigDecimal.valueOf(100)).toPlainString() + "%), indicating lower profitability.";
        } else if (averageGrossProfitMargin.compareTo(BigDecimal.valueOf(0.80)) <= 0) { // 50% to 80%
            score = 2;
            explanation = "Average Gross Margin is between 50% and 80% (" + averageGrossProfitMargin.multiply(BigDecimal.valueOf(100)).toPlainString() + "%), indicating healthy profitability.";
        } else { // > 80%
            score = 3;
            explanation = "Average Gross Margin is greater than 80% (" + averageGrossProfitMargin.multiply(BigDecimal.valueOf(100)).toPlainString() + "%), indicating very strong profitability.";
        }

        return new FerolReportItem("grossMargin", score, explanation);
    }
}
