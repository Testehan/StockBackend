package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.filing.SecFiling;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.QuoteService;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import com.testehan.finana.util.SafeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ValuationCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValuationCalculator.class);

    @Value("classpath:/prompts/100Bagger/valuation_prompt.txt")
    private Resource valuationPrompt;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final QuoteService quoteService;
    private final EarningsHistoryRepository earningsHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SafeParser safeParser;
    private final LlmService llmService;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final EarningsEstimatesRepository earningsEstimatesRepository;
    private final SecFilingRepository secFilingRepository;

    public ValuationCalculator(CompanyOverviewRepository companyOverviewRepository, QuoteService quoteService, EarningsHistoryRepository earningsHistoryRepository, ApplicationEventPublisher eventPublisher, SafeParser safeParser, LlmService llmService, FinancialRatiosRepository financialRatiosRepository, EarningsEstimatesRepository earningsEstimatesRepository, SecFilingRepository secFilingRepository) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.quoteService = quoteService;
        this.earningsHistoryRepository = earningsHistoryRepository;
        this.eventPublisher = eventPublisher;
        this.safeParser = safeParser;
        this.llmService = llmService;
        this.financialRatiosRepository = financialRatiosRepository;
        this.earningsEstimatesRepository = earningsEstimatesRepository;
        this.secFilingRepository = secFilingRepository;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);

        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);

        StringBuilder riskFactors = new StringBuilder();
        StringBuilder managementDiscussion = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            riskFactors.append(latestTenKFiling.getRiskFactors());
                            managementDiscussion.append(latestTenKFiling.getManagementDiscussion());
                        });
            } else {
                var errorMessage = "No 10k found for ticker: " + ticker;
                eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
                LOGGER.error(errorMessage);
            }
        }, () -> {
            var errorMessage = "No 10k found for ticker: " + ticker;
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
        });

        var currentPe = calculateCurrentPE(ticker).block();
        var peg = calculatePegRatio(ticker).block();
        var medianPe = calculateMedianPeRatio(ticker);
        var year1EpsCagr = calculate1YrForwardEpsGrowth(ticker);
        var year3EpsCagr = calculate3YrForwardEpsGrowth(ticker);

        PromptTemplate promptTemplate = new PromptTemplate(valuationPrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("current_pe", currentPe);
        promptParameters.put("peg_ratio", peg);
        promptParameters.put("median_pe", medianPe);
        promptParameters.put("forward_growth_year1", year1EpsCagr);
        promptParameters.put("forward_growth_year3", year3EpsCagr);
        promptParameters.put("risk_factors", riskFactors);
        promptParameters.put("mda", managementDiscussion);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Sending data to LLM for valuation analysis..."));
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlmWithOllama(prompt, "valuation_analysis", ticker);
            eventPublisher.publishEvent(new MessageEvent(this, ticker, sseEmitter, "Received LLM response for valuation analysis."));
            LlmScoreExplanationResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("valuationContext", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateValuation' failed.";
            eventPublisher.publishEvent(new ErrorEvent(this, ticker, sseEmitter, new RuntimeException(errorMessage)));
            LOGGER.error(errorMessage);
            return new ReportItem("valuationContext", -10, "Operation 'calculateValuation' failed.");
        }

    }

    public Mono<BigDecimal> calculateCurrentPE(String ticker) {
        return quoteService.getLastStockQuote(ticker)
                .flatMap(lastStockQuote -> {
                    if (lastStockQuote == null) {
                        LOGGER.warn("Could not retrieve latest stock price for ticker: {}", ticker);
                        return Mono.empty();
                    }
                    BigDecimal price = new BigDecimal(lastStockQuote.getAdjOpen());

                    Optional<EarningsHistory> earningsHistoryOptional = earningsHistoryRepository.findBySymbol(ticker);
                    if (earningsHistoryOptional.isEmpty() || Objects.isNull(earningsHistoryOptional.get().getQuarterlyEarnings())) {
                        LOGGER.warn("No earnings history data for P/E calculation for ticker: {}", ticker);
                        return Mono.empty();
                    }

                    List<QuarterlyEarning> quarterlyEarnings = earningsHistoryOptional.get().getQuarterlyEarnings();
                    quarterlyEarnings.sort(Comparator.comparing(QuarterlyEarning::getFiscalDateEnding).reversed());

                    var last4quarters = quarterlyEarnings.stream()
                            .filter(qe -> Objects.nonNull(qe.getReportedEPS()))
                            .sorted(Comparator.comparing(QuarterlyEarning::getFiscalDateEnding).reversed())
                            .limit(4) // We only care about the last 4 quarters
                            .collect(Collectors.toList());

                    if (last4quarters.size() < 4) {
                        LOGGER.warn("Not enough quarterly earnings with reported EPS to calculate TTM EPS for {}. Needed 4, but found {}", ticker, last4quarters.size());
                        return Mono.empty();
                    }

                    BigDecimal ttmEps = last4quarters.stream()
                            .map(qe -> safeParser.parse(qe.getReportedEPS()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (ttmEps.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.empty();
                    }

                    return Mono.just(price.divide(ttmEps, 2, RoundingMode.HALF_UP));
                });
    }

    public BigDecimal calculateMedianPeRatio(String ticker) {
        Optional<FinancialRatiosData> financialRatiosDataOptional = financialRatiosRepository.findBySymbol(ticker);
        if (financialRatiosDataOptional.isEmpty() || financialRatiosDataOptional.get().getAnnualReports().isEmpty()) {
            return null;
        }

        List<FinancialRatiosReport> annualReports = financialRatiosDataOptional.get().getAnnualReports();
        List<BigDecimal> peRatios = annualReports.stream()
                .sorted(Comparator.comparing(FinancialRatiosReport::getDate).reversed())
                .limit(5)
                .map(FinancialRatiosReport::getPeRatio)
                .filter(Objects::nonNull)
                .filter(pe -> pe.compareTo(BigDecimal.ZERO) > 0)
                .sorted()
                .collect(Collectors.toList());

        if (peRatios.isEmpty()) {
            return null;
        }

        BigDecimal medianPe;
        int size = peRatios.size();
        if (size % 2 == 1) {
            medianPe = peRatios.get(size / 2);
        } else {
            medianPe = peRatios.get(size / 2 - 1).add(peRatios.get(size / 2)).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        }

        return medianPe;
    }

    public BigDecimal calculate1YrForwardEpsGrowth(String ticker) {
        Optional<EarningsEstimate> earningsEstimateOptional = earningsEstimatesRepository.findBySymbol(ticker);
        if (earningsEstimateOptional.isEmpty() || earningsEstimateOptional.get().getEstimates().size() < 2) {
            return null;
        }

        List<Estimate> estimates = earningsEstimateOptional.get().getEstimates();
        estimates.sort(Comparator.comparing(Estimate::getDate));

        LocalDate currentDate = LocalDate.now();
        Estimate currentYearEstimate = estimates.stream()
                .filter(e -> LocalDate.parse(e.getDate()).getYear() == currentDate.getYear())
                .findFirst()
                .orElse(null);

        Estimate nextYearEstimate = estimates.stream()
                .filter(e -> LocalDate.parse(e.getDate()).getYear() == currentDate.getYear() + 1)
                .findFirst()
                .orElse(null);

        if (currentYearEstimate == null || nextYearEstimate == null) {
            return null;
        }

        BigDecimal currentEps = safeParser.parse(currentYearEstimate.getEpsAvg());
        BigDecimal nextEps = safeParser.parse(nextYearEstimate.getEpsAvg());

        if (currentEps.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return (nextEps.subtract(currentEps)).divide(currentEps, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
    }

    public BigDecimal calculate3YrForwardEpsGrowth(String ticker) {
        Optional<EarningsEstimate> earningsEstimateOptional = earningsEstimatesRepository.findBySymbol(ticker);
        if (earningsEstimateOptional.isEmpty() || earningsEstimateOptional.get().getEstimates().size() < 4) {
            return null;
        }

        List<Estimate> estimates = earningsEstimateOptional.get().getEstimates();
        estimates.sort(Comparator.comparing(Estimate::getDate));

        LocalDate currentDate = LocalDate.now();
        Estimate currentYearEstimate = estimates.stream()
                .filter(e -> LocalDate.parse(e.getDate()).getYear() == currentDate.getYear())
                .findFirst()
                .orElse(null);

        Estimate year3Estimate = estimates.stream()
                .filter(e -> LocalDate.parse(e.getDate()).getYear() == currentDate.getYear() + 3)
                .findFirst()
                .orElse(null);

        if (currentYearEstimate == null || year3Estimate == null) {
            return null;
        }

        BigDecimal beginningEps = safeParser.parse(currentYearEstimate.getEpsAvg());
        BigDecimal endingEps = safeParser.parse(year3Estimate.getEpsAvg());

        if (beginningEps.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        double cagr = Math.pow(endingEps.divide(beginningEps, 4, RoundingMode.HALF_UP).doubleValue(), 1.0 / 3.0) - 1;

        return new BigDecimal(cagr).multiply(new BigDecimal("100"));
    }

    public Mono<BigDecimal> calculatePegRatio(String ticker) {
        return calculateCurrentPE(ticker)
                .flatMap(currentPe -> {
                    BigDecimal oneYearForwardEpsGrowth = calculate1YrForwardEpsGrowth(ticker);

                    if (oneYearForwardEpsGrowth == null) {
                        LOGGER.warn("1-Year Forward EPS Growth is null for PEG ratio calculation for ticker: {}", ticker);
                        return Mono.empty();
                    }

                    // PEG ratio is typically calculated using growth rate as a whole number (e.g., 10 for 10%), not decimal (0.10)
                    // Also, growth should be positive for a meaningful PEG ratio
                    if (oneYearForwardEpsGrowth.compareTo(BigDecimal.ZERO) <= 0) {
                        LOGGER.warn("1-Year Forward EPS Growth is zero or negative for PEG ratio calculation for ticker: {}. Returning null.", ticker);
                        return Mono.empty();
                    }

                    // Convert percentage growth rate to a decimal for division, then multiply by 100 to get the whole number
                    // Example: 20% growth rate would be 20. So PE / 20.
                    return Mono.just(currentPe.divide(oneYearForwardEpsGrowth, 2, RoundingMode.HALF_UP));
                });
    }
}