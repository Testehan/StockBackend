package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.EarningsHistory;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.QuarterlyEarning;
import com.testehan.finana.repository.EarningsHistoryRepository;
import com.testehan.finana.service.reporting.FerolSseService;
import com.testehan.finana.util.SafeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EpsCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(EpsCalculator.class);

    private final EarningsHistoryRepository earningsHistoryRepository;
    private final FerolSseService ferolSseService;
    private final SafeParser safeParser;

    public EpsCalculator(EarningsHistoryRepository earningsHistoryRepository, FerolSseService ferolSseService, SafeParser safeParser) {
        this.earningsHistoryRepository = earningsHistoryRepository;
        this.ferolSseService = ferolSseService;
        this.safeParser = safeParser;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        ferolSseService.sendSseEvent(sseEmitter, "Calculating Earnings Per Share (EPS)...");

        Optional<EarningsHistory> earningsHistoryOptional = earningsHistoryRepository.findBySymbol(ticker);

        if (earningsHistoryOptional.isEmpty() || Objects.isNull(earningsHistoryOptional.get().getQuarterlyEarnings())
                || earningsHistoryOptional.get().getQuarterlyEarnings().size() < 8) {
            LOGGER.warn("No sufficient earnings history data for EPS calculation for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "EPS calculation skipped: Insufficient data found.");
            return new FerolReportItem("earningsPerShare", 0, "Insufficient quarterly earnings history data for EPS calculation (need at least 8 quarters).");
        }

        List<QuarterlyEarning> quarterlyEarnings = earningsHistoryOptional.get().getQuarterlyEarnings();
        // Sort by reportedDate in descending order to get latest first
        quarterlyEarnings.sort(Comparator.comparing(QuarterlyEarning::getFiscalDateEnding).reversed());

        // Get latest 8 quarters for current and previous TTM EPS
        List<QuarterlyEarning> relevantEarnings = quarterlyEarnings.stream().limit(8).collect(Collectors.toList());

        // Calculate current TTM EPS (latest 4 quarters)
        BigDecimal currentTtmEps = BigDecimal.ZERO;
        for (int i = 0; i < 4; i++) {
            currentTtmEps = currentTtmEps.add(safeParser.parse(relevantEarnings.get(i).getReportedEPS()));
        }

        // Calculate previous TTM EPS (the 4 quarters before the current TTM)
        BigDecimal previousTtmEps = BigDecimal.ZERO;
        for (int i = 4; i < 8; i++) {
            previousTtmEps = previousTtmEps.add(safeParser.parse(relevantEarnings.get(i).getReportedEPS()));
        }

        ferolSseService.sendSseEvent(sseEmitter, "Current TTM EPS: " + currentTtmEps.toPlainString());
        ferolSseService.sendSseEvent(sseEmitter, "Previous TTM EPS: " + previousTtmEps.toPlainString());

        int score;
        String explanation;
        StringBuilder detailedExplanation = new StringBuilder();
        detailedExplanation.append("Current TTM EPS: ").append(currentTtmEps.toPlainString()).append(". ");

        if (currentTtmEps.compareTo(BigDecimal.ZERO) < 0) { // Negative EPS
            score = 0;
            explanation = "EPS is Negative, indicating a potential 'Cash Burner'.";
        } else {
            // EPS is positive, now check for growth
            if (previousTtmEps.compareTo(BigDecimal.ZERO) <= 0) { // Previous TTM EPS was zero or negative
                if (currentTtmEps.compareTo(BigDecimal.ZERO) > 0) {
                    score = 1;
                    explanation = "EPS is Positive, but previous TTM EPS was zero or negative, so growth cannot be reliably assessed as 'Growing Fast'.";
                } else { // Should not happen given outer if, but for completeness
                    score = 0;
                    explanation = "EPS is Negative, indicating a potential 'Cash Burner'.";
                }
            } else {
                BigDecimal growthThreshold = BigDecimal.valueOf(1.15); // 15% higher

                if (currentTtmEps.compareTo(previousTtmEps.multiply(growthThreshold)) >= 0) { // Current is >= 15% higher than previous
                    score = 3;
                    BigDecimal growthPercentage = currentTtmEps.subtract(previousTtmEps)
                            .divide(previousTtmEps, 4, BigDecimal.ROUND_HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    detailedExplanation.append("YoY Growth: ").append(growthPercentage.toPlainString()).append("%. ");
                    explanation = "EPS is Positive and growing fast (Current TTM is " + growthPercentage.toPlainString() + "% higher than Previous TTM), indicating strong performance.";
                } else {
                    score = 2; // Changed from 1 to 2 as per user's clarification
                    BigDecimal growthPercentage = currentTtmEps.subtract(previousTtmEps)
                            .divide(previousTtmEps, 4, BigDecimal.ROUND_HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    detailedExplanation.append("YoY Growth: ").append(growthPercentage.toPlainString()).append("%. ");
                    explanation = "EPS is Positive, but not growing fast (Current TTM is " + growthPercentage.toPlainString() + "% higher than Previous TTM), indicating stable performance.";
                }
            }
        }
        ferolSseService.sendSseEvent(sseEmitter, "EPS calculation complete. Score: " + score);
        return new FerolReportItem("earningsPerShare", score, detailedExplanation.toString() + explanation);
    }
}
