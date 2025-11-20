package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.quote.IndexData;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.service.QuoteService;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.util.SafeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class PerformanceVsSP500Calculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceVsSP500Calculator.class);

    public static final String S_P_500 = "^GSPC";
    private final QuoteService quoteService;
    private final ApplicationEventPublisher eventPublisher;

    public PerformanceVsSP500Calculator(QuoteService quoteService, ApplicationEventPublisher eventPublisher) {
        this.quoteService = quoteService;
        this.eventPublisher = eventPublisher;
    }

    public ReportItem calculateUpsidePerformance(String ticker, SseEmitter sseEmitter) {
        Optional<GlobalQuote> startStockQuoteOpt = quoteService.getFirstStockQuote(ticker);
        Optional<GlobalQuote> endStockQuoteOpt = quoteService.getLastStockQuote(ticker).blockOptional();

        if (startStockQuoteOpt.isEmpty() || endStockQuoteOpt.isEmpty()) {
            var errorMessage = "Stock price data not available for ticker " +ticker;
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
            return new ReportItem("performanceVsIndex", 0, "Stock price data not available.");
        }

        LocalDate stockStartDate = LocalDate.parse(startStockQuoteOpt.get().getDate());
        LocalDate stockEndDate = LocalDate.parse(endStockQuoteOpt.get().getDate());

        Optional<IndexData> startIndexQuoteOpt = quoteService.getIndexQuoteByDate(S_P_500, stockStartDate);
        Optional<IndexData> endIndexQuoteOpt = quoteService.getIndexQuoteByDate(S_P_500, stockEndDate);

        if (startIndexQuoteOpt.isEmpty() || endIndexQuoteOpt.isEmpty()) {
            var errorMessage = "S&P 500 price data for the corresponding period not available.";
            LOGGER.error("S&P 500 price data for the corresponding period not available.");
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("performanceVsIndex", 0, "S&P 500 price data for the corresponding period not available.");
        }

        Double stockStartPrice = SafeParser.tryParseDouble(startStockQuoteOpt.get().getAdjClose());
        Double stockEndPrice = SafeParser.tryParseDouble(endStockQuoteOpt.get().getAdjClose());
        Double indexStartPrice = startIndexQuoteOpt.get().getPrice();
        Double indexEndPrice = endIndexQuoteOpt.get().getPrice();

        if (stockStartPrice == null || stockEndPrice == null || indexStartPrice == null || indexEndPrice == null || stockStartPrice == 0 || indexStartPrice == 0) {
            var errorMessage = "Could not parse price data.";
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
            return new ReportItem("performanceVsIndex", 0, "Could not parse price data.");
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

        return new ReportItem("performanceVsIndex", score, comment);
    }

    public ReportItem calculateDownsidePerformance(String ticker, SseEmitter sseEmitter) {
        Optional<GlobalQuote> startStockQuoteOpt = quoteService.getFirstStockQuote(ticker);
        Optional<GlobalQuote> endStockQuoteOpt = quoteService.getLastStockQuote(ticker).blockOptional();

        if (startStockQuoteOpt.isEmpty() || endStockQuoteOpt.isEmpty()) {
            var errorMessage = "Stock price data not available for ticker " +ticker;
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
            return new ReportItem("bigMarketLoser", 0, "Stock price data not available.");
        }

        LocalDate stockStartDate = LocalDate.parse(startStockQuoteOpt.get().getDate());
        LocalDate stockEndDate = LocalDate.parse(endStockQuoteOpt.get().getDate());

        Optional<IndexData> startIndexQuoteOpt = quoteService.getIndexQuoteByDate(S_P_500, stockStartDate);
        Optional<IndexData> endIndexQuoteOpt = quoteService.getIndexQuoteByDate(S_P_500, stockEndDate);

        if (startIndexQuoteOpt.isEmpty() || endIndexQuoteOpt.isEmpty()) {
            var errorMessage = "S&P 500 price data for the corresponding period not available.";
            LOGGER.error("S&P 500 price data for the corresponding period not available.");
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            return new ReportItem("bigMarketLoser", 0, "S&P 500 price data for the corresponding period not available.");
        }

        Double stockStartPrice = SafeParser.tryParseDouble(startStockQuoteOpt.get().getAdjClose());
        Double stockEndPrice = SafeParser.tryParseDouble(endStockQuoteOpt.get().getAdjClose());
        Double indexStartPrice = startIndexQuoteOpt.get().getPrice();
        Double indexEndPrice = endIndexQuoteOpt.get().getPrice();

        if (stockStartPrice == null || stockEndPrice == null || indexStartPrice == null || indexEndPrice == null || stockStartPrice == 0 || indexStartPrice == 0) {
            var errorMessage = "Could not parse price data.";
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
            return new ReportItem("bigMarketLoser", 0, "Could not parse price data.");
        }

        double stockPerformance = (stockEndPrice - stockStartPrice) / stockStartPrice;
        double indexPerformance = (indexEndPrice - indexStartPrice) / indexStartPrice;
        double performanceDifference = stockPerformance - indexPerformance;

        int score = 0;
        if (performanceDifference >= 0) score = 0;
        if (performanceDifference < -0.25) score = -3;
        if (performanceDifference < -0.50) score = -5;

        long days = ChronoUnit.DAYS.between(stockStartDate, stockEndDate);
        String comment = String.format("Over a period of %d days, stock performance: %.2f%%, S&P 500 performance: %.2f%%. Difference: %.2f%%.",
                days, stockPerformance * 100, indexPerformance * 100, performanceDifference * 100);

        return new ReportItem("bigMarketLoser", score, comment);
    }

}
