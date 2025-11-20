package com.testehan.finana.service.periodic;

import com.testehan.finana.model.GlobalQuote;
import com.testehan.finana.model.UserStock;
import com.testehan.finana.model.UserStockStatus;
import com.testehan.finana.model.valuation.Valuations;
import com.testehan.finana.model.valuation.dcf.*;
import com.testehan.finana.model.valuation.growth.GrowthOutput;
import com.testehan.finana.model.valuation.growth.GrowthValuation;
import com.testehan.finana.repository.UserStockRepository;
import com.testehan.finana.repository.ValuationsRepository;
import com.testehan.finana.service.QuoteService;
import com.testehan.finana.service.ValuationAlert;
import com.testehan.finana.service.ValuationAlertService;
import com.testehan.finana.service.valuation.DcfValuationService;
import com.testehan.finana.service.valuation.GrowthValuationService;
import com.testehan.finana.service.valuation.ReverseDcfValuationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PeriodicPriceAlerter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicPriceAlerter.class);
    private static final String USER_ID = "dante";
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final UserStockRepository userStockRepository;
    private final ValuationsRepository valuationsRepository;
    private final QuoteService quoteService;
    private final DcfValuationService dcfValuationService;
    private final GrowthValuationService growthValuationService;
    private final ReverseDcfValuationService reverseDcfValuationService;
    private final ValuationAlertService valuationAlertService;

    public PeriodicPriceAlerter(UserStockRepository userStockRepository,
                                ValuationsRepository valuationsRepository,
                                QuoteService quoteService,
                                DcfValuationService dcfValuationService,
                                GrowthValuationService growthValuationService,
                                ReverseDcfValuationService reverseDcfValuationService,
                                ValuationAlertService valuationAlertService) {
        this.userStockRepository = userStockRepository;
        this.valuationsRepository = valuationsRepository;
        this.quoteService = quoteService;
        this.dcfValuationService = dcfValuationService;
        this.growthValuationService = growthValuationService;
        this.reverseDcfValuationService = reverseDcfValuationService;
        this.valuationAlertService = valuationAlertService;
    }

    @Scheduled(fixedRate = 600_000)
    public void checkAndAlertPriceChanges() {
        LOGGER.info("Starting periodic price alert check for user: {}", USER_ID);

        List<UserStock> relevantStocks = userStockRepository.findByUserId(USER_ID).stream()
                .filter(stock -> stock.getStatus() == UserStockStatus.OWNED || 
                                 stock.getStatus() == UserStockStatus.BUY_CANDIDATE)
                .toList();

        if (relevantStocks.isEmpty()) {
            LOGGER.info("No stocks with status OWNED or BUY_CANDIDATE found for user: {}", USER_ID);
            return;
        }

        LOGGER.info("Found {} stocks to monitor for user {}:", relevantStocks.size(), USER_ID);

        for (UserStock stock : relevantStocks) {
            String ticker = stock.getStockId();
            LOGGER.info("  - Ticker: {}, Status: {}, Notes: {}", 
                    ticker, 
                    stock.getStatus(),
                    stock.getNotes() != null ? stock.getNotes() : "No notes");

            processTicker(ticker);
        }
    }

    private void processTicker(String ticker) {
        Optional<Valuations> valuationsOpt = valuationsRepository.findById(ticker);

        if (valuationsOpt.isEmpty()) {
            LOGGER.info("    No valuations found for {}", ticker);
            return;
        }

        Valuations valuations = valuationsOpt.get();
        BigDecimal latestPrice = getLatestStockPrice(ticker);

        if (latestPrice == null || latestPrice.compareTo(BigDecimal.ZERO) == 0) {
            LOGGER.warn("    Could not get latest price for {}", ticker);
            return;
        }

        LOGGER.info("    Latest price: ${}", latestPrice);

        processDcfValuations(ticker, valuations, latestPrice);
        processGrowthValuations(ticker, valuations, latestPrice);
        processReverseDcfValuations(ticker, valuations, latestPrice);
    }

    private void processDcfValuations(String ticker, Valuations valuations, BigDecimal latestPrice) {
        if (valuations.getDcfValuations().isEmpty()) {
            return;
        }

        var latestDcf = valuations.getDcfValuations().get(valuations.getDcfValuations().size() - 1);
        BigDecimal originalPrice = latestDcf.getDcfCalculationData().meta().currentSharePrice();

        if (!isValidPrice(originalPrice)) {
            return;
        }

        logPriceChange("DCF", originalPrice, latestPrice);

        try {
            DcfCalculationData updatedData = dcfValuationService.getDcfCalculationData(ticker);
            DcfOutput newOutput = dcfValuationService.calculateDcfValuation(updatedData, latestDcf.getDcfUserInput());

            if (newOutput.intrinsicValuePerShare() != null) {
                String verdict = newOutput.verdict();
                logValuationResult("DCF", latestPrice, newOutput.intrinsicValuePerShare(), verdict);

                if ("Undervalued".equals(verdict) || "Neutral".equals(verdict)) {
                    saveDcfValuation(ticker, latestDcf.getDcfUserInput(), updatedData, newOutput, latestPrice);
                }
            }
        } catch (Exception e) {
            LOGGER.error("    Error recalculating DCF for {}: {}", ticker, e.getMessage());
        }
    }

    private void processGrowthValuations(String ticker, Valuations valuations, BigDecimal latestPrice) {
        if (valuations.getGrowthValuations().isEmpty()) {
            return;
        }

        var latestGrowth = valuations.getGrowthValuations().get(valuations.getGrowthValuations().size() - 1);
        BigDecimal originalPrice = latestGrowth.getGrowthValuationData().getCurrentSharePrice();

        if (!isValidPrice(originalPrice)) {
            return;
        }

        logPriceChange("Growth", originalPrice, latestPrice);

        try {
            GrowthValuation freshData = growthValuationService.getGrowthCompanyValuationData(ticker);
            freshData.setGrowthUserInput(latestGrowth.getGrowthUserInput());
            GrowthOutput newOutput = growthValuationService.calculateGrowthCompanyValuation(freshData);

            if (newOutput.getIntrinsicValuePerShare() != null) {
                String verdict = newOutput.getVerdict();
                logValuationResult("Growth", latestPrice, newOutput.getIntrinsicValuePerShare(), verdict);

                if ("Undervalued".equals(verdict) || "Neutral".equals(verdict)) {
                    saveGrowthValuation(ticker, freshData, newOutput, latestPrice);
                }
            }
        } catch (Exception e) {
            LOGGER.error("    Error recalculating Growth for {}: {}", ticker, e.getMessage());
        }
    }

    private void processReverseDcfValuations(String ticker, Valuations valuations, BigDecimal latestPrice) {
        if (valuations.getReverseDcfValuations().isEmpty()) {
            return;
        }

        var latestReverse = valuations.getReverseDcfValuations().get(valuations.getReverseDcfValuations().size() - 1);
        BigDecimal originalPrice = latestReverse.getDcfCalculationData().meta().currentSharePrice();

        if (!isValidPrice(originalPrice)) {
            return;
        }

        logPriceChange("Reverse DCF", originalPrice, latestPrice);

        try {
            DcfCalculationData updatedData = dcfValuationService.getDcfCalculationData(ticker);
            ReverseDcfOutput newOutput = reverseDcfValuationService.calculateReverseDcfValuation(
                    updatedData, 
                    latestReverse.getReverseDcfUserInput()
            );

            if (newOutput.impliedFCFGrowthRate() != null) {
                LOGGER.info("    Reverse DCF recalculated: Implied FCF growth rate: {}%", 
                        String.format("%.2f", newOutput.impliedFCFGrowthRate() * 100));

                String verdict = newOutput.verdict();
                if ("Undervalued".equals(verdict) || "Neutral".equals(verdict)) {
                    saveReverseDcfValuation(ticker, latestReverse.getReverseDcfUserInput(), updatedData, newOutput, latestPrice);
                }
            }
        } catch (Exception e) {
            LOGGER.error("    Error recalculating Reverse DCF for {}: {}", ticker, e.getMessage());
        }
    }

    private void saveDcfValuation(String ticker, DcfUserInput userInput, DcfCalculationData calculationData, 
                                  DcfOutput output, BigDecimal currentPrice) {
        try {
            userInput.setUserComments("Generated");

            DcfValuation dcfValuation = new DcfValuation();
            dcfValuation.setValuationDate(LocalDateTime.now().toString());
            dcfValuation.setDcfUserInput(userInput);
            dcfValuation.setDcfCalculationData(calculationData);
            dcfValuation.setDcfOutput(output);

            Valuations valuations = valuationsRepository.findById(ticker).orElse(new Valuations());
            valuations.setTicker(ticker);
            
            valuations.getDcfValuations().removeIf(v -> "Generated".equals(v.getDcfUserInput().getUserComments()));
            
            valuations.getDcfValuations().add(dcfValuation);
            valuationsRepository.save(valuations);

            LOGGER.info("    Saved DCF valuation for {}", ticker);
            
            ValuationAlert alert = ValuationAlert.fromValuation(
                    ticker, "DCF", output.verdict(), 
                    currentPrice, output.intrinsicValuePerShare(), dcfValuation);
            valuationAlertService.pushValuationAlert(alert);
        } catch (Exception e) {
            LOGGER.error("    Error saving DCF valuation for {}: {}", ticker, e.getMessage());
        }
    }

    private void saveGrowthValuation(String ticker, GrowthValuation growthValuation, 
                                     GrowthOutput output, BigDecimal currentPrice) {
        try {
            growthValuation.getGrowthUserInput().setUserComments("Generated");
            growthValuation.setValuationDate(LocalDateTime.now().toString());
            growthValuation.setGrowthOutput(output);

            Valuations valuations = valuationsRepository.findById(ticker).orElse(new Valuations());
            valuations.setTicker(ticker);
            
            valuations.getGrowthValuations().removeIf(v -> "Generated".equals(v.getGrowthUserInput().getUserComments()));
            
            valuations.getGrowthValuations().add(growthValuation);
            valuationsRepository.save(valuations);

            LOGGER.info("    Saved Growth valuation for {}", ticker);
            
            ValuationAlert alert = ValuationAlert.fromValuation(
                    ticker, "Growth", output.getVerdict(), 
                    currentPrice, output.getIntrinsicValuePerShare(), growthValuation);
            valuationAlertService.pushValuationAlert(alert);
        } catch (Exception e) {
            LOGGER.error("    Error saving Growth valuation for {}: {}", ticker, e.getMessage());
        }
    }

    private void saveReverseDcfValuation(String ticker, ReverseDcfUserInput userInput, DcfCalculationData calculationData, 
                                        ReverseDcfOutput output, BigDecimal currentPrice) {
        try {
            userInput.setUserComments("Generated");

            ReverseDcfValuation reverseDcfValuation = new ReverseDcfValuation();
            reverseDcfValuation.setValuationDate(LocalDateTime.now().toString());
            reverseDcfValuation.setReverseDcfUserInput(userInput);
            reverseDcfValuation.setDcfCalculationData(calculationData);
            reverseDcfValuation.setReverseDcfOutput(output);

            Valuations valuations = valuationsRepository.findById(ticker).orElse(new Valuations());
            valuations.setTicker(ticker);
            
            valuations.getReverseDcfValuations().removeIf(v -> "Generated".equals(v.getReverseDcfUserInput().getUserComments()));
            
            valuations.getReverseDcfValuations().add(reverseDcfValuation);
            valuationsRepository.save(valuations);

            LOGGER.info("    Saved Reverse DCF valuation for {}", ticker);
            
            ValuationAlert alert = ValuationAlert.fromValuation(
                    ticker, "Reverse DCF", output.verdict(), 
                    currentPrice, null, reverseDcfValuation);
            valuationAlertService.pushValuationAlert(alert);
        } catch (Exception e) {
            LOGGER.error("    Error saving Reverse DCF valuation for {}: {}", ticker, e.getMessage());
        }
    }

    private boolean isValidPrice(BigDecimal price) {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }

    private void logPriceChange(String valuationType, BigDecimal originalPrice, BigDecimal latestPrice) {
        double priceChange = calculatePercentageChange(originalPrice, latestPrice);
        LOGGER.info("    {}: Original price was ${}, change: {}%", 
                valuationType, originalPrice, String.format("%.2f", priceChange));
    }

    private void logValuationResult(String valuationType, BigDecimal latestPrice, 
                                    BigDecimal intrinsicValue, String verdict) {
        double upside = calculatePercentageChange(latestPrice, intrinsicValue);
        LOGGER.info("    {} recalculated: Intrinsic value: ${}, Upside: {}%, Verdict: {}", 
                valuationType, intrinsicValue, String.format("%.2f", upside), verdict);
    }

    private double calculatePercentageChange(BigDecimal original, BigDecimal current) {
        return current.subtract(original, getMathContext())
                .divide(original, getMathContext())
                .doubleValue() * 100;
    }

    private BigDecimal getLatestStockPrice(String ticker) {
        try {
            Optional<GlobalQuote> quoteOpt = quoteService.getLastStockQuote(ticker).blockOptional();
            if (quoteOpt.isPresent()) {
                GlobalQuote quote = quoteOpt.get();
                if (quote.getPrice() != null && !quote.getPrice().isEmpty()) {
                    return new BigDecimal(quote.getPrice());
                }
                if (quote.getAdjClose() != null && !quote.getAdjClose().isEmpty()) {
                    return new BigDecimal(quote.getAdjClose());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("    Error getting latest quote for {}: {}", ticker, e.getMessage());
        }
        return null;
    }

    private MathContext getMathContext() {
        return new MathContext(SCALE, ROUNDING_MODE);
    }
}
