package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.EarningsHistory;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.QuarterlyEarning;
import com.testehan.finana.repository.EarningsHistoryRepository;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.reporting.FerolSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BeatingEarningsExpectationsCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(BeatingEarningsExpectationsCalculator.class);

    private final EarningsHistoryRepository earningsHistoryRepository;
    private final FerolSseService ferolSseService;

    public BeatingEarningsExpectationsCalculator(FinancialDataService financialDataService, EarningsHistoryRepository earningsHistoryRepository, FerolSseService ferolSseService) {
        this.earningsHistoryRepository = earningsHistoryRepository;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculateUpsidePerformance(String ticker, SseEmitter sseEmitter) {
        Optional<EarningsHistory> earningsHistory = earningsHistoryRepository.findBySymbol(ticker);

        if (earningsHistory.isPresent() && Objects.nonNull(earningsHistory.get().getQuarterlyEarnings())
                && !earningsHistory.get().getQuarterlyEarnings().isEmpty()){

            var quarterlyEarningsHistory = earningsHistory.get().getQuarterlyEarnings();
            var last4quarters = quarterlyEarningsHistory.stream()
                    .sorted(Comparator.comparing(QuarterlyEarning::getFiscalDateEnding).reversed())
                    .limit(4) // We only care about the last 4 quarters
                    .collect(Collectors.toList());

            double score = 0;
            int noBigBeats = 0;
            int noMediumBeats = 0;
            int noMisses = 0;
            for (QuarterlyEarning earning : last4quarters){
                if (Double.valueOf(earning.getSurprisePercentage()) > 50){
                    score = score + 1;
                    noBigBeats++;
                    continue;
                }
                if (Double.valueOf(earning.getSurprisePercentage()) > 0){
                    score = score + 0.5;
                    noMediumBeats++;
                }
                if (Double.valueOf(earning.getSurprisePercentage()) < 0){
                    score = score - 1;
                    noMisses++;
                }
            }

            if (score < 0){
                score = 0;
            }

            return new FerolReportItem("consistentlyBeatExpectations", (int) score, "Big Beats --> " + noBigBeats + "       medium beats --> " + noMediumBeats + "       and misses --> " + noMisses);

        } else {
            ferolSseService.sendSseErrorEvent(sseEmitter, "Could not get earnings history for ticker " + ticker);
            LOGGER.error("Could not get earnings history for ticker {}", ticker);
            return new FerolReportItem("consistentlyBeatExpectations", 0, "Could not get earnings history for ticker " + ticker);
        }
    }
}
