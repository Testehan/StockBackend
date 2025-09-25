package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.GlobalQuote;
import com.testehan.finana.model.IndexData;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.reporting.FerolSseService;
import com.testehan.finana.util.SafeParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class PerformanceVsSP500Calculator {

    public static final String S_P_500 = "^GSPC";
    private final FinancialDataService financialDataService;
    private final FerolSseService ferolSseService;

    @Autowired
    public PerformanceVsSP500Calculator(FinancialDataService financialDataService, FerolSseService ferolSseService) {
        this.financialDataService = financialDataService;
        this.ferolSseService = ferolSseService;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<GlobalQuote> startStockQuoteOpt = financialDataService.getFirstStockQuote(ticker);
        Optional<GlobalQuote> endStockQuoteOpt = financialDataService.getLastStockQuote(ticker).blockOptional();

        if (startStockQuoteOpt.isEmpty() || endStockQuoteOpt.isEmpty()) {
            ferolSseService.sendSseErrorEvent(sseEmitter, "Stock price data not available.");
            return new FerolReportItem("performanceVsIndex", 0, "Stock price data not available.");
        }

        LocalDate stockStartDate = LocalDate.parse(startStockQuoteOpt.get().getDate());
        LocalDate stockEndDate = LocalDate.parse(endStockQuoteOpt.get().getDate());

        Optional<IndexData> startIndexQuoteOpt = financialDataService.getIndexQuoteByDate(S_P_500, stockStartDate);
        Optional<IndexData> endIndexQuoteOpt = financialDataService.getIndexQuoteByDate(S_P_500, stockEndDate);

        if (startIndexQuoteOpt.isEmpty() || endIndexQuoteOpt.isEmpty()) {
            ferolSseService.sendSseErrorEvent(sseEmitter, "S&P 500 price data for the corresponding period not available.");
            return new FerolReportItem("performanceVsIndex", 0, "S&P 500 price data for the corresponding period not available.");
        }

        Double stockStartPrice = SafeParser.tryParseDouble(startStockQuoteOpt.get().getAdjClose());
        Double stockEndPrice = SafeParser.tryParseDouble(endStockQuoteOpt.get().getAdjClose());
        Double indexStartPrice = startIndexQuoteOpt.get().getPrice();
        Double indexEndPrice = endIndexQuoteOpt.get().getPrice();

        if (stockStartPrice == null || stockEndPrice == null || indexStartPrice == null || indexEndPrice == null || stockStartPrice == 0 || indexStartPrice == 0) {
            ferolSseService.sendSseErrorEvent(sseEmitter, "Could not parse price data.");
            return new FerolReportItem("performanceVsIndex", 0, "Could not parse price data.");
        }

        double stockPerformance = (stockEndPrice - stockStartPrice) / stockStartPrice;
        double indexPerformance = (indexEndPrice - indexStartPrice) / indexStartPrice;
        double performanceDifference = stockPerformance - indexPerformance;

        int score = 0;
        if (performanceDifference > 0) score = 1;
        if (performanceDifference > 0.25) score = 2;
        if (performanceDifference > 0.50) score = 3;
        if (performanceDifference > 1.00) score = 4;

        long days = ChronoUnit.DAYS.between(stockStartDate, stockEndDate);
        String comment = String.format("Over a period of %d days, stock performance: %.2f%%, S&P 500 performance: %.2f%%. Difference: %.2f%%.",
                days, stockPerformance * 100, indexPerformance * 100, performanceDifference * 100);

        return new FerolReportItem("performanceVsIndex", score, comment);
    }

}
