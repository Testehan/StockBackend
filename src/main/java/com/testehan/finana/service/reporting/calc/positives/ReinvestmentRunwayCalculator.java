package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.ChecklistSseService;
import com.testehan.finana.util.DateUtils;
import com.testehan.finana.util.SafeParser;
import org.jetbrains.annotations.NotNull;
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
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReinvestmentRunwayCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReinvestmentRunwayCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final SecFilingRepository secFilingRepository;
    private final RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    private final RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository;
    private final FinancialDataService financialDataService;
    private final LlmService llmService;
    private final ChecklistSseService checklistSseService;
    private final SafeParser safeParser;
    private final DateUtils dateUtils;
    private final CashFlowRepository cashFlowRepository;

    @Value("classpath:/prompts/100Bagger/reinvestment_runway_prompt.txt")
    private Resource reinvestmentRunwayPrompt;

    public ReinvestmentRunwayCalculator(CompanyOverviewRepository companyOverviewRepository, IncomeStatementRepository incomeStatementRepository, SecFilingRepository secFilingRepository, RevenueSegmentationDataRepository revenueSegmentationDataRepository, RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository, FinancialDataService financialDataService, LlmService llmService, ChecklistSseService checklistSseService, SafeParser safeParser, DateUtils dateUtils, CashFlowRepository cashFlowRepository) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.secFilingRepository = secFilingRepository;
        this.revenueSegmentationDataRepository = revenueSegmentationDataRepository;
        this.revenueGeographicSegmentationRepository = revenueGeographicSegmentationRepository;
        this.financialDataService = financialDataService;
        this.llmService = llmService;
        this.checklistSseService = checklistSseService;
        this.safeParser = safeParser;
        this.dateUtils = dateUtils;
        this.cashFlowRepository = cashFlowRepository;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()) {
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new ReportItem("reinvestmentRunway", 0, "Something went wrong and score could not be calculated ");
        }

        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);
        if (incomeStatementDataOptional.isEmpty()) {
            LOGGER.warn("No income statement data found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No income statement data found for ticker " + ticker);
            return new ReportItem("reinvestmentRunway", 0, "Something went wrong and score could not be calculated ");
        }

        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder managementDiscussion = new StringBuilder();
        StringBuilder businessDescription = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            managementDiscussion.append(latestTenKFiling.getManagementDiscussion());
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                checklistSseService.sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
        });

        var latestEarningsTranscript = getLatestEarningsTranscript(ticker);
        BigDecimal revenueCAGR3y = calculateRevenueCAGR3y(ticker);
        BigDecimal[] ttmRevenue = getTtmRevenue(ticker, incomeStatementDataOptional);
        List<RevenueSegmentationReport> lastFiveYearsRevSegmentation = getRevenueSegmentationLast5Years(ticker, sseEmitter);
        List<RevenueGeographicSegmentationReport> lastFiveYearsRevenueGeographic = getRevenueGeographicSegmentationLast5years(ticker, sseEmitter);
        var acquisitionHistoryAsPercentOfRevenue = calculateAcquisitionsToRevenueRatio(ticker);

        PromptTemplate promptTemplate = new PromptTemplate(reinvestmentRunwayPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        var llmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("ttm_revenue", ttmRevenue[0].toPlainString());
        promptParameters.put("rev_cagr_3y", revenueCAGR3y);
        promptParameters.put("business_description", businessDescription.toString());
        promptParameters.put("management_discussion", managementDiscussion.toString());
        promptParameters.put("latest_earnings_transcript", latestEarningsTranscript);
        promptParameters.put("revenue_segmentation", lastFiveYearsRevSegmentation);
        promptParameters.put("revenue_geographic_segmentation", lastFiveYearsRevenueGeographic);
        promptParameters.put("acquisition_history_summary", acquisitionHistoryAsPercentOfRevenue);

        promptParameters.put("format", llmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            checklistSseService.sendSseEvent(sseEmitter, "Sending data to LLM for reinvestment runway analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            checklistSseService.sendSseEvent(sseEmitter, "Received LLM response for reinvestment runway analysis.");
            LlmScoreExplanationResponse convertedLlmResponse = llmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("reinvestmentRunway", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateReinvestmentRunway' failed.";
            LOGGER.error(errorMessage, e);
            checklistSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new ReportItem("reinvestmentRunway", -10, "Operation 'calculateReinvestmentRunway' failed.");
        }
    }

    private List<RevenueGeographicSegmentationReport> getRevenueGeographicSegmentationLast5years(String ticker, SseEmitter sseEmitter) {
        List<RevenueGeographicSegmentationReport> lastFiveYearsRevenueGeographic = new ArrayList<>();
        Optional<RevenueGeographicSegmentationData> revenueGeographicSegmentationDataOptional = revenueGeographicSegmentationRepository.findBySymbol(ticker);
        if (revenueGeographicSegmentationDataOptional.isEmpty()) {
            LOGGER.warn("No revenue geographic segmentation data found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No revenue geographic segmentation data found for ticker " + ticker);
        } else {
            var revenueSegmentationData = revenueGeographicSegmentationDataOptional.get();

            List<Integer> lastFiveYears = revenueSegmentationData.getReports().stream()
                    .map(RevenueGeographicSegmentationReport::getFiscalYear)
                    .filter(Objects::nonNull)                    // safety
                    .distinct()                                  // remove duplicates
                    .sorted(Comparator.reverseOrder())          // newest first
                    .limit(5)                                    // take only 5
                    .collect(Collectors.toList());

            // Step 2: Filter the original stream to keep only reports from those years
            lastFiveYearsRevenueGeographic = revenueSegmentationData.getReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getFiscalYear()))
                    .collect(Collectors.toList());
        }

        return lastFiveYearsRevenueGeographic;
    }

    @NotNull
    private List<RevenueSegmentationReport> getRevenueSegmentationLast5Years(String ticker, SseEmitter sseEmitter) {
        List<RevenueSegmentationReport> lastFiveYearsReports = new ArrayList<>();
        Optional<RevenueSegmentationData> revenueSegmentationOptional = revenueSegmentationDataRepository.findBySymbol(ticker);
        if (revenueSegmentationOptional.isEmpty()) {
            LOGGER.warn("No revenue segmentation data found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No revenue segmentation data found for ticker " + ticker);
        } else {
            var revenueSegmentationData = revenueSegmentationOptional.get();

            List<Integer> lastFiveYears = revenueSegmentationData.getAnnualReports().stream()
                    .map(RevenueSegmentationReport::getFiscalYear)
                    .filter(Objects::nonNull)                    // safety
                    .distinct()                                  // remove duplicates
                    .sorted(Comparator.reverseOrder())          // newest first
                    .limit(5)                                    // take only 5
                    .collect(Collectors.toList());

            // Step 2: Filter the original stream to keep only reports from those years
            lastFiveYearsReports = revenueSegmentationData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getFiscalYear()))
                    .collect(Collectors.toList());
        }
        return lastFiveYearsReports;
    }

    private BigDecimal[] getTtmRevenue(String ticker, Optional<IncomeStatementData> incomeStatementDataOptional) {
        final BigDecimal[] ttmRevenue = {BigDecimal.ZERO};
        incomeStatementDataOptional.ifPresent(income -> {
            List<IncomeReport> quarterlyReports = income.getQuarterlyReports().stream()
                    .sorted(Comparator.comparing(report -> ((IncomeReport) report).getDate()).reversed())
                    .limit(4)
                    .collect(Collectors.toList());

            if (quarterlyReports.isEmpty()) {
                LOGGER.warn("No quarterly income reports found for ticker: {}", ticker);
                return;
            }

            for (IncomeReport report : quarterlyReports) {
                ttmRevenue[0] = ttmRevenue[0].add(safeParser.parse(report.getRevenue()));
            }
        });
        return ttmRevenue;
    }

    @NotNull
    public String getLatestEarningsTranscript(String ticker) {
        var latestQuarter = dateUtils.getDateQuarter(financialDataService.getLatestReportedDate(ticker));
        var latestEarningsTranscript = financialDataService.getEarningsCallTranscript(ticker, latestQuarter).block().getTranscript().stream()
                .map(t -> t.getSpeaker() + " (" + t.getTitle() + "): " + t.getContent() + " [" + t.getSentiment() + "]")
                .collect(Collectors.joining("\n"));
        return latestEarningsTranscript;
    }

    private BigDecimal calculateRevenueCAGR3y(String ticker) {
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

        if (oldRevenue.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warn("Initial revenue was zero or negative, cannot calculate CAGR for ticker: " + ticker);
            return BigDecimal.ZERO;
        }

        double ratio = latestRevenue.divide(oldRevenue, 4, java.math.RoundingMode.HALF_UP).doubleValue();
        double cagr = Math.pow(ratio, 1.0 / 3.0) - 1.0;
        return BigDecimal.valueOf(cagr * 100).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public String calculateAcquisitionsToRevenueRatio(String ticker) {
        Optional<CashFlowData> cashFlowDataOptional = cashFlowRepository.findBySymbol(ticker);
        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);

        if (cashFlowDataOptional.isEmpty() || incomeStatementDataOptional.isEmpty()) {
            LOGGER.warn("No cash flow or income statement data found for ticker: {}", ticker);
            return "Not enough data to calculate acquisitions to revenue ratio.";
        }

        List<CashFlowReport> cashFlowReports = cashFlowDataOptional.get().getAnnualReports().stream()
                .sorted(Comparator.comparing(CashFlowReport::getDate).reversed())
                .limit(5)
                .collect(Collectors.toList());

        List<IncomeReport> incomeReports = incomeStatementDataOptional.get().getAnnualReports().stream()
                .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                .collect(Collectors.toList());

        StringBuilder result = new StringBuilder();
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
        currencyFormatter.setMaximumFractionDigits(0);


        for (CashFlowReport cashFlowReport : cashFlowReports) {
            incomeReports.stream()
                    .filter(incomeReport -> incomeReport.getDate().equals(cashFlowReport.getDate()))
                    .findFirst()
                    .ifPresent(incomeReport -> {
                        BigDecimal acquisitionsNet = safeParser.parse(cashFlowReport.getAcquisitionsNet());

                        // We are interested in cash used for acquisitions, which is an outflow (negative number).
                        // If it's positive, it's a divestment, so we ignore it.
                        if (acquisitionsNet.compareTo(BigDecimal.ZERO) < 0) {
                            BigDecimal acquisitions = acquisitionsNet.negate(); // make it positive for display
                            BigDecimal revenue = safeParser.parse(incomeReport.getRevenue());

                            if (revenue.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal ratio = acquisitions.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                                String formattedAcquisitions = currencyFormatter.format(acquisitions.divide(BigDecimal.valueOf(1_000_000))) + "M";
                                result.append(String.format("FY%s: %s (%d%% of revenue)\n",
                                        cashFlowReport.getFiscalYear(),
                                        formattedAcquisitions,
                                        ratio.intValue()));
                            }
                        }
                    });
        }

        return result.toString().trim();
    }
}
