package com.testehan.finana.service.reporting.calc.negatives;

import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.RevenueGeographicSegmentationReport;
import com.testehan.finana.repository.RevenueGeographicSegmentationRepository;
import com.testehan.finana.service.reporting.ChecklistSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CurrencyRiskCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrencyRiskCalculator.class);

    private final RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository;
    private final ChecklistSseService ferolSseService;

    public CurrencyRiskCalculator(RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository, ChecklistSseService ferolSseService) {
        this.revenueGeographicSegmentationRepository = revenueGeographicSegmentationRepository;
        this.ferolSseService = ferolSseService;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        var revenueGeographyOptional = revenueGeographicSegmentationRepository.findBySymbol(ticker);

        if (revenueGeographyOptional.isPresent() && Objects.nonNull(revenueGeographyOptional.get().getReports())
                &&!revenueGeographyOptional.get().getReports().isEmpty())
        {
            var revenueGeography = revenueGeographyOptional.get();
            List<RevenueGeographicSegmentationReport> revenueGeographyReports = revenueGeography.getReports();

            List<RevenueGeographicSegmentationReport> sortedRevenueGeographic = revenueGeographyReports.stream()
                    .sorted(Comparator.comparing(RevenueGeographicSegmentationReport::getDate).reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            return calculateAverageCurrencyRisk(sortedRevenueGeographic);

        } else {
            String errorMessage = "Operation 'calculateCurrencyRisk' failed.";
            LOGGER.error(errorMessage + " No geographic revenue data found for {}",ticker);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
        }

        return new ReportItem("currencyRisk", -10, "Something went wrong and score could not be calculated ");


    }

    /**
     * Calculates risk based on foreign revenue percentage.
     * > 75% Foreign -> -2
     * > 50% Foreign -> -1
     * <= 50% Foreign -> 0
     */
    public ReportItem calculateAverageCurrencyRisk(List<RevenueGeographicSegmentationReport> reports) {

        // 2. Select the last 5 years (or fewer if data is missing)
        int yearsToCalculate = Math.min(reports.size(), 5);
        double sumForeignPercentage = 0.0;

        for (int i = 0; i < yearsToCalculate; i++) {
            RevenueGeographicSegmentationReport report = reports.get(i);
            Map<String, String> segments = report.getData();

            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal domesticRevenue = BigDecimal.ZERO;

            var domesticKey = identifyDomesticKey(segments);

            if (Objects.nonNull(domesticKey)) {
                // Calculate totals for this specific year
                for (Map.Entry<String, String> entry : segments.entrySet()) {
                    BigDecimal val = new BigDecimal(entry.getValue());
                    totalRevenue = totalRevenue.add(val);

                    // Check if this key matches the domestic key (case-insensitive)
                    if (entry.getKey().toLowerCase().contains(domesticKey.toLowerCase())) {
                        domesticRevenue = val;
                    }
                }
            } else {
                return new ReportItem("currencyRisk", -2, "The company gets revenue from these areas: " + String.join(", ", segments.keySet()));
            }

            if (totalRevenue.equals(BigDecimal.ZERO)) continue;

            BigDecimal foreignRevenue = totalRevenue.subtract(domesticRevenue);
            double foreignPct = foreignRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP).doubleValue();
            sumForeignPercentage += foreignPct;
        }

        // 3. Calculate Average
        double averageForeignPct = sumForeignPercentage / yearsToCalculate;

        // 4. Determine Score
        int averageCurrencyRiskScore;
        if (averageForeignPct > 0.75) {
            averageCurrencyRiskScore = -2;
        } else if (averageForeignPct > 0.50) {
            averageCurrencyRiskScore = -1;
        } else {
            averageCurrencyRiskScore = 0;
        }

        return new ReportItem("currencyRisk", averageCurrencyRiskScore, "Over the last " + yearsToCalculate + " years the sum of foreign revenue % of total revenue is " + sumForeignPercentage * 100 + "% . This means an average of " + averageForeignPct * 100 + "% per year");
    }

    private String identifyDomesticKey(Map<String, String> segments) {
        // 1. EXACT/HIGH PRIORITY MATCHES (Best quality)
        List<String> highPriority = Arrays.asList(
                "United States", "USA", "U.S.", "United States of America", "Domestic", "US"
        );

        // 2. REGIONAL MATCHES (Acceptable if US not broken out)
        List<String> mediumPriority = Arrays.asList(
                "North America", "United States and Canada", "US & Canada"
        );

        // 3. BROAD MATCHES (Fallback for companies like Apple)
        List<String> lowPriority = Arrays.asList(
                "Americas", "The Americas", "Americas Segment"
        );

        // Iterate through segments to find the best match
        // We use loops to check priorities in order

        // Check High Priority
        for (String key : segments.keySet()) {
            if (containsIgnoreCase(key, highPriority)) return key;
        }

        // Check Medium Priority
        for (String key : segments.keySet()) {
            if (containsIgnoreCase(key, mediumPriority)) return key;
        }

        // Check Low Priority
        for (String key : segments.keySet()) {
            if (containsIgnoreCase(key, lowPriority)) return key;
        }

        return null; // Could not identify domestic segment
    }

    private static boolean containsIgnoreCase(String key, List<String> targets) {
        for (String target : targets) {
            // We use exact match or "Starts With" to avoid false positives
            // (e.g. avoiding "Rest of Americas" if we are looking for "Americas")
            if (key.equalsIgnoreCase(target)) return true;
            if (key.toLowerCase().startsWith(target.toLowerCase())) return true;
        }
        return false;
    }

}
