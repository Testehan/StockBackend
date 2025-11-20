package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.service.reporting.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public RoicCalculator(FinancialRatiosRepository financialRatiosRepository, ApplicationEventPublisher eventPublisher) {
        this.financialRatiosRepository = financialRatiosRepository;
        this.eventPublisher = eventPublisher;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Calculating Return on Invested Capital (ROIC)..."));
        Optional<FinancialRatiosData> financialRatiosData = financialRatiosRepository.findBySymbol(ticker);

        if (financialRatiosData.isEmpty() || financialRatiosData.get().getAnnualReports().isEmpty()) {
            LOGGER.warn("No annual financial ratios data found for ticker: {}", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "ROIC calculation skipped: No annual data found."));
            return new ReportItem("roic", 0, "No annual financial ratios data available.");
        }

        // Get annual ROIC for 5-year median and for the latest value
        List<FinancialRatiosReport> annualReports = financialRatiosData.get().getAnnualReports().stream()
                .filter(report -> report.getRoic() != null)
                .sorted(Comparator.comparing(FinancialRatiosReport::getDate).reversed())
                .toList();

        if (annualReports.isEmpty()) {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "ROIC calculation skipped: No annual ROIC data found."));
            return new ReportItem("roic", 0, "No annual ROIC data available.");
        }

        BigDecimal latestAnnualRoic = annualReports.get(0).getRoic();
        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Latest Annual ROIC: " + latestAnnualRoic.multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%"));


        List<BigDecimal> annualRoicValues = annualReports.stream()
                .limit(5)
                .map(FinancialRatiosReport::getRoic)
                .collect(Collectors.toList());

        if (annualRoicValues.isEmpty()) {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "ROIC calculation skipped: No annual ROIC data found for median."));
            return new ReportItem("roic", 0, "No annual ROIC data available for median calculation.");
        }

        // Calculate 5-year median ROIC
        Collections.sort(annualRoicValues);
        BigDecimal medianRoic;
        int middle = annualRoicValues.size() / 2;
        if (annualRoicValues.size() % 2 == 1) {
            medianRoic = annualRoicValues.get(middle);
        } else {
            medianRoic = (annualRoicValues.get(middle - 1).add(annualRoicValues.get(middle))).divide(BigDecimal.valueOf(2));
        }
        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "5-Year Median ROIC: " + medianRoic.multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%"));

        int score = 0;
        String explanation;

        if (latestAnnualRoic.compareTo(BigDecimal.valueOf(0.08)) < 0) { // ROIC < 8%
            score = 0;
            explanation = "ROIC is less than 8% (" + latestAnnualRoic.multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%), indicating the company might be destroying value.";
        } else if (latestAnnualRoic.compareTo(BigDecimal.valueOf(0.12)) < 0) { // ROIC 8% - 12%
            score = 1;
            explanation = "ROIC is between 8% and 12% (" + latestAnnualRoic.multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%), indicating the company is essentially breaking even on its capital costs.";
        } else if (latestAnnualRoic.compareTo(BigDecimal.valueOf(0.20)) < 0) { // ROIC 12% - 20%
            score = 2;
            explanation = "ROIC is between 12% and 20% (" + latestAnnualRoic.multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%), indicating a solid compounder.";
        } else { // ROIC > 20%
            score = 3;
            explanation = "ROIC is greater than 20% (" + latestAnnualRoic.multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%), indicating a strong competitive advantage.";
        }

        // Apply "Rising Rule"
        BigDecimal marginOfSafety = BigDecimal.valueOf(0.01); // 1% margin of safety
        if (latestAnnualRoic.compareTo(medianRoic.add(marginOfSafety)) > 0 && score < 3) {
            score++;
            explanation += " Additionally, ROIC is rising compared to the 5-year median, suggesting an improving trend.";
        }
        eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "ROIC calculation complete. Score: " + score));

        return new ReportItem("roic", score, explanation);
    }
}
