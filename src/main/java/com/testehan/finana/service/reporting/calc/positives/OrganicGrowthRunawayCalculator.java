package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.llm.responses.FerolLlmResponse;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.ChecklistSseService;
import com.testehan.finana.util.SafeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrganicGrowthRunawayCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrganicGrowthRunawayCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final SecFilingRepository secFilingRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final FinancialRatiosRepository financialRatiosRepository;

    private final LlmService llmService;
    private final ChecklistSseService ferolSseService;
    private final OptionalityCalculator optionalityCalculator;
    private final SafeParser safeParser;


    @Value("classpath:/prompts/organic_growth_runaway_prompt.txt")
    private Resource organicGrowthPrompt;

    public OrganicGrowthRunawayCalculator(CompanyOverviewRepository companyOverviewRepository, SecFilingRepository secFilingRepository, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository, FinancialRatiosRepository financialRatiosRepository, LlmService llmService, ChecklistSseService ferolSseService, OptionalityCalculator optionalityCalculator, SafeParser safeParser) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.secFilingRepository = secFilingRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.financialRatiosRepository = financialRatiosRepository;
        this.llmService = llmService;
        this.ferolSseService = ferolSseService;
        this.optionalityCalculator = optionalityCalculator;
        this.safeParser = safeParser;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);

        BigDecimal revenueCAGRPerShare = calculateRevenueCAGRPerShare(ticker);
        BigDecimal sustainableGrowthRate = calculateSustainableGrowthRate(ticker);

        StringBuilder stringBuilder = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getManagementDiscussion());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
        });

        var latestEarningsTranscript = optionalityCalculator.getLatestEarningsTranscript(ticker);

        PromptTemplate promptTemplate = new PromptTemplate(organicGrowthPrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("management_discussion", stringBuilder.toString());
        promptParameters.put("latest_earnings_transcript", latestEarningsTranscript);
        promptParameters.put("revenue_cagr_share", revenueCAGRPerShare);
        promptParameters.put("sustainable_growth_rate", sustainableGrowthRate);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            ferolSseService.sendSseEvent(sseEmitter, "Sending data to LLM for organic growth runaway analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            ferolSseService.sendSseEvent(sseEmitter, "Received LLM response for organic growth runaway analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("organicGrowthRunway", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateOrganicGrowthRunaway' failed.";
            LOGGER.error(errorMessage, e);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new ReportItem("organicGrowthRunway", -10, "Operation 'calculateOrganicGrowthRunaway' failed.");
        }
    }

    private BigDecimal calculateRevenueCAGRPerShare(String ticker) {
        Optional<IncomeStatementData> incomeStatementDataOpt = incomeStatementRepository.findBySymbol(ticker);

        if (incomeStatementDataOpt.isEmpty() || incomeStatementDataOpt.get().getAnnualReports() == null || incomeStatementDataOpt.get().getAnnualReports().size() < 4) {
            LOGGER.warn("Not enough annual income reports for {}. Found {}.", ticker, incomeStatementDataOpt.map(d -> d.getAnnualReports().size()).orElse(0));
            return BigDecimal.ZERO;
        }

        List<IncomeReport> annualReports = incomeStatementDataOpt.get().getAnnualReports().stream()
                .sorted(Comparator.comparing(IncomeReport::getDate))
                .collect(Collectors.toList());

        IncomeReport latestReport = annualReports.get(annualReports.size() - 1);
        IncomeReport oldReport = annualReports.get(annualReports.size() - 4);

        BigDecimal latestRevenue = safeParser.parse(latestReport.getRevenue());
        BigDecimal oldRevenue = safeParser.parse(oldReport.getRevenue());

        IncomeReport latestSharesReport = findClosestSharesOutstanding(annualReports, latestReport.getDate());
        IncomeReport oldSharesReport = findClosestSharesOutstanding(annualReports, oldReport.getDate());

        if (latestSharesReport == null || oldSharesReport == null) {
            LOGGER.warn("Could not find matching shares outstanding data for the period for ticker: " + ticker);
            return BigDecimal.ZERO;
        }

        BigDecimal latestShares = safeParser.parse(latestSharesReport.getWeightedAverageShsOutDil());
        BigDecimal oldShares = safeParser.parse(oldSharesReport.getWeightedAverageShsOutDil());

        if (latestShares.compareTo(BigDecimal.ZERO) == 0 || oldShares.compareTo(BigDecimal.ZERO) == 0) {
            LOGGER.warn("Shares outstanding is zero, cannot calculate per share value for ticker: " + ticker);
            return BigDecimal.ZERO;
        }

        BigDecimal latestRevenuePerShare = latestRevenue.divide(latestShares, 4, java.math.RoundingMode.HALF_UP);
        BigDecimal oldRevenuePerShare = oldRevenue.divide(oldShares, 4, java.math.RoundingMode.HALF_UP);

        if (oldRevenuePerShare.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warn("Initial revenue per share was zero or negative, cannot calculate CAGR for ticker: " + ticker);
            return BigDecimal.ZERO;
        }

        double ratio = latestRevenuePerShare.divide(oldRevenuePerShare, 4, java.math.RoundingMode.HALF_UP).doubleValue();
        double cagr = Math.pow(ratio, 1.0 / 3.0) - 1.0;
        return BigDecimal.valueOf(cagr * 100).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSustainableGrowthRate(String ticker) {
        Optional<FinancialRatiosData> financialRatiosDataOpt = financialRatiosRepository.findBySymbol(ticker);

        if (financialRatiosDataOpt.isEmpty() || financialRatiosDataOpt.get().getAnnualReports() == null || financialRatiosDataOpt.get().getAnnualReports().size() < 3) {
            LOGGER.warn("Not enough annual financial ratios reports for SGR calculation for ticker: {}. Found {}.", ticker, financialRatiosDataOpt.map(d -> d.getAnnualReports().size()).orElse(0));
            return BigDecimal.ZERO;
        }

        List<com.testehan.finana.model.FinancialRatiosReport> annualReports = financialRatiosDataOpt.get().getAnnualReports().stream()
                .sorted(Comparator.comparing(com.testehan.finana.model.FinancialRatiosReport::getDate).reversed())
                .limit(3)
                .collect(Collectors.toList());

        if (annualReports.size() < 3) {
            LOGGER.warn("Not enough annual financial ratios reports for SGR calculation for ticker: {}. Found {}.", ticker, annualReports.size());
            return BigDecimal.ZERO;
        }

        List<BigDecimal> sgrValues = new ArrayList<>();
        for (com.testehan.finana.model.FinancialRatiosReport report : annualReports) {
            BigDecimal roic = report.getRoic();
            BigDecimal dividendPayoutRatio = report.getDividendPayoutRatio();

            if (roic != null && dividendPayoutRatio != null) {
                BigDecimal retentionRatio = BigDecimal.ONE.subtract(dividendPayoutRatio);
                BigDecimal sgr = roic.multiply(retentionRatio);
                sgrValues.add(sgr);
            }
        }

        if (sgrValues.isEmpty()) {
            LOGGER.warn("No SGR values could be calculated for ticker: {}", ticker);
            return BigDecimal.ZERO;
        }

        BigDecimal sumSgr = sgrValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgSgr = sumSgr.divide(new BigDecimal(sgrValues.size()), 4, java.math.RoundingMode.HALF_UP);

        return avgSgr.multiply(BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private IncomeReport findClosestSharesOutstanding(List<IncomeReport> reports, String dateString) {
        try {
            LocalDate targetDate = LocalDate.parse(dateString);
            return reports.stream()
                    .min(Comparator.comparing(report -> {
                        try {
                            LocalDate reportDate = LocalDate.parse(report.getDate());
                            return Math.abs(ChronoUnit.DAYS.between(targetDate, reportDate));
                        } catch (java.time.format.DateTimeParseException e) {
                            return Long.MAX_VALUE;
                        }
                    }))
                    .orElse(null);
        } catch (java.time.format.DateTimeParseException e) {
            LOGGER.warn("Could not parse date: " + dateString, e);
            return null;
        }
    }
}
