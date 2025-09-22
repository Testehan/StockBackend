package com.testehan.finana.service.reporting;

import com.testehan.finana.model.*;
import com.testehan.finana.model.llm.responses.FerolLlmResponse;
import com.testehan.finana.model.llm.responses.FerolMoatAnalysisLlmResponse;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.util.DateUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class FerolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FerolService.class);

    private final BalanceSheetRepository balanceSheetRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final LlmService llmService;
    private final GeneratedReportRepository generatedReportRepository;
    private final FinancialDataService financialDataService;
    private final FinancialRatiosRepository financialRatiosRepository;
    private final EarningsHistoryRepository earningsHistoryRepository;
    private final SecFilingRepository secFilingRepository;
    private final CompanyOverviewRepository companyOverviewRepository;
    private final SharesOutstandingRepository sharesOutstandingRepository;
    private final EarningsEstimatesRepository earningsEstimatesRepository;
    private final RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    private final DateUtils dateUtils;

    @Value("classpath:/prompts/financial_resilience_prompt.txt")
    private Resource financialResiliencePrompt;

    @Value("classpath:/prompts/moat_prompt.txt")
    private Resource moatPrompt;

    @Value("classpath:/prompts/optionality_prompt.txt")
    private Resource optionalityPrompt;

    @Value("classpath:/prompts/organic_growth_runaway_prompt.txt")
    private Resource organicGrowthPrompt;

    @Value("classpath:/prompts/top_dog_prompt.txt")
    private Resource topDogPrompt;

    @Value("classpath:/prompts/operating_leverage_prompt.txt")
    private Resource operatingLeveragePrompt;

    @Value("classpath:/prompts/customers_cyclicality.txt")
    private Resource companyCyclicalityPrompt;

    @Value("classpath:/prompts/recurring_revenue_prompt.txt")
    private Resource recurringRevenuePrompt;

    @Value("classpath:/prompts/pricing_power_prompt.txt")
    private Resource pricingPowerPrompt;

    public FerolService(BalanceSheetRepository balanceSheetRepository,
                        IncomeStatementRepository incomeStatementRepository,
                        LlmService llmService,
                        GeneratedReportRepository generatedReportRepository,
                        FinancialDataService financialDataService,
                        FinancialRatiosRepository financialRatiosRepository,
                        EarningsHistoryRepository earningsHistoryRepository, SecFilingRepository secFilingRepository, CompanyOverviewRepository companyOverviewRepository, CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository, DateUtils dateUtils, SharesOutstandingRepository sharesOutstandingRepository, EarningsEstimatesRepository earningsEstimatesRepository, RevenueSegmentationDataRepository revenueSegmentationDataRepository) {
        this.balanceSheetRepository = balanceSheetRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.llmService = llmService;
        this.generatedReportRepository = generatedReportRepository;
        this.financialDataService = financialDataService;
        this.financialRatiosRepository = financialRatiosRepository;
        this.earningsHistoryRepository = earningsHistoryRepository;
        this.secFilingRepository = secFilingRepository;
        this.companyOverviewRepository = companyOverviewRepository;
        this.dateUtils = dateUtils;
        this.sharesOutstandingRepository = sharesOutstandingRepository;
        this.earningsEstimatesRepository = earningsEstimatesRepository;
        this.revenueSegmentationDataRepository = revenueSegmentationDataRepository;
    }

    private BigDecimal safeParseBigDecimal(String value) {
        if (value == null || value.equalsIgnoreCase("None")) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    public SseEmitter getFerolReport(String ticker, boolean recreateReport) {
        SseEmitter sseEmitter = new SseEmitter(3600000L); // Timeout set to 1 hour

        new Thread(() -> {
            try {
                if (!recreateReport) {
                    sendSseEvent(sseEmitter, "Attempting to load report from database...");
                    Optional<GeneratedReport> existingGeneratedReport = generatedReportRepository.findBySymbol(ticker);
                    if (existingGeneratedReport.isPresent() && existingGeneratedReport.get().getFerolReport() != null) {
                        FerolReport ferolReport = existingGeneratedReport.get().getFerolReport();
                        sendSseEvent(sseEmitter, "Report loaded from database.");
                        sseEmitter.send(SseEmitter.event()
                                .name("COMPLETED")
                                .data(ferolReport, MediaType.APPLICATION_JSON));
                        sseEmitter.complete();
                        LOGGER.info("FEROL report for {} loaded from DB and sent.", ticker);
                        return; // Exit as report is sent
                    } else {
                        sendSseEvent(sseEmitter, "Report not found in database or incomplete, generating new report.");
                    }
                } else {
                    sendSseEvent(sseEmitter, "Initiating FEROL report generation for " + ticker + "...");
                }

                sendSseEvent(sseEmitter, "Ensuring financial data is present...");
                financialDataService.ensureFinancialDataIsPresent(ticker);
                sendSseEvent(sseEmitter, "Financial data check complete.");

                sendSseEvent(sseEmitter, "Fetching income statement and balance sheet data...");
                Optional<IncomeStatementData> incomeStatementData = incomeStatementRepository.findBySymbol(ticker);
                Optional<BalanceSheetData> balanceSheetData = balanceSheetRepository.findBySymbol(ticker);
                sendSseEvent(sseEmitter, "Financial data retrieved.");

                // Launch all calculations in parallel using CompletableFuture
//                CompletableFuture<FerolReportItem> financialResilienceFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Calculating financial resilience...");
//                    FerolReportItem item = calculateFinancialResilience(ticker, incomeStatementData, balanceSheetData, sseEmitter);
//                    sendSseEvent(sseEmitter, "Financial resilience calculation complete.");
//                    return item;
//                });
//
//                CompletableFuture<FerolReportItem> grossMarginFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Calculating Gross Margin...");
//                    FerolReportItem item = calculateGrossMargin(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Gross Margin calculation complete.");
//                    return item;
//                });
//
//                CompletableFuture<FerolReportItem> roicFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Calculating Return on Invested Capital (ROIC)...");
//                    FerolReportItem item = calculateReturnOnInvestedCapital(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Return on Invested Capital (ROIC) calculation complete.");
//                    return item;
//                });
//
//                CompletableFuture<FerolReportItem> fcfFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Calculating Free Cash Flow...");
//                    FerolReportItem item = calculateFreeCashFlow(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Free Cash Flow calculation complete.");
//                    return item;
//                });
//
//                CompletableFuture<FerolReportItem> epsFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Calculating Earnings Per Share (EPS)...");
//                    FerolReportItem item = calculateEarningsPerShare(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Earnings Per Share (EPS) calculation complete.");
//                    return item;
//                });
//
//                Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
//                Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
//                CompletableFuture<FerolMoatAnalysisLlmResponse> moatsFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Thinking about moats...");
//                    FerolMoatAnalysisLlmResponse item = calculateMoats(ticker, secFilingData,companyOverview, sseEmitter);
//                    sendSseEvent(sseEmitter, "Moats analysis is complete.");
//                    return item;
//                });
//
//                CompletableFuture<FerolReportItem> optionalityFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Thinking about optionality...");
//                    FerolReportItem item = calculateOptionality(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Optionality analysis is complete.");
//                    return item;
//                });
//
//                CompletableFuture<FerolReportItem> organicGrowthRunawayFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Thinking about organic growth runaway...");
//                    FerolReportItem item = calculateOrganicGrowthRunaway(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Organic growth runaway analysis is complete.");
//                    return item;
//                });
//
//                CompletableFuture<FerolReportItem> topDogFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Thinking about top dog or first mover...");
//                    FerolReportItem item = calculateTopDogOrFirstMover(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Top dog or first mover analysis is complete.");
//                    return item;
//                });
//
//                CompletableFuture<FerolReportItem> operatingLeverageFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Thinking about operating leverage...");
//                    FerolReportItem item = calculateOperatingLeverage(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Operating leverage analysis is complete.");
//                    return item;
//                });
//
//                CompletableFuture<FerolReportItem> acquisitionsFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Thinking about Sales & Marketing % of gross profit...");
//                    FerolReportItem item = calculateAcquisitions(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Sales & Marketing % of gross profit analysis is complete.");
//                    return item;
//                });

//                CompletableFuture<FerolReportItem> cyclicalityFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Thinking how cyclical is this business..");
//                    FerolReportItem item = calculateCyclicality(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Business cyclicality analysis is complete.");
//                    return item;
//                });

//                CompletableFuture<FerolReportItem> recurringRevenueFuture = CompletableFuture.supplyAsync(() -> {
//                    sendSseEvent(sseEmitter, "Does this company have recurring revenue..?");
//                    FerolReportItem item = calculateRecurringRevenue(ticker, sseEmitter);
//                    sendSseEvent(sseEmitter, "Recurring revenue analysis is complete.");
//                    return item;
//                });

                CompletableFuture<FerolReportItem> pricingPowerFuture = CompletableFuture.supplyAsync(() -> {
                    sendSseEvent(sseEmitter, "How about their pricing power..?");
                    FerolReportItem item = calculatePricingPower(ticker, sseEmitter);
                    sendSseEvent(sseEmitter, "Pricing power analysis is complete.");
                    return item;
                });

                // Wait for all futures to complete
                CompletableFuture.allOf(
          //              financialResilienceFuture, grossMarginFuture, roicFuture, fcfFuture, epsFuture,
             //           moatsFuture,
            //            optionalityFuture, organicGrowthRunawayFuture, topDogFuture, operatingLeverageFuture,
                 //       acquisitionsFuture,
//                        cyclicalityFuture,
                        //recurringRevenueFuture,
                            pricingPowerFuture)
                .join(); // Blocks until all complete or one fails

                // Financials
                List<FerolReportItem> ferolReportItems = new ArrayList<>();
//                ferolReportItems.add(financialResilienceFuture.get());
//                ferolReportItems.add(grossMarginFuture.get());
//                ferolReportItems.add(roicFuture.get());
//                ferolReportItems.add(fcfFuture.get());
//                ferolReportItems.add(epsFuture.get());

                // Moat
//                FerolMoatAnalysisLlmResponse moatAnalysis = moatsFuture.get();
//                ferolReportItems.add(new FerolReportItem("networkEffect",moatAnalysis.getNetworkEffectScore(), moatAnalysis.getNetworkEffectExplanation()));
//                ferolReportItems.add(new FerolReportItem("switchingCosts",moatAnalysis.getSwitchingCostsScore(), moatAnalysis.getSwitchingCostsExplanation()));
//                ferolReportItems.add(new FerolReportItem("durableCostAdvantage",moatAnalysis.getDurableCostAdvantageScore(), moatAnalysis.getDurableCostAdvantageExplanation()));
//                ferolReportItems.add(new FerolReportItem("intangibles",moatAnalysis.getIntangiblesScore(), moatAnalysis.getIntangiblesExplanation()));
//                ferolReportItems.add(new FerolReportItem("counterPositioning",moatAnalysis.getCounterPositioningScore(), moatAnalysis.getCounterPositioningExplanation()));
//                ferolReportItems.add(new FerolReportItem("moatDirection",moatAnalysis.getMoatDirectionScore(), moatAnalysis.getMoatDirectionExplanation()));

//                // Potential
//                ferolReportItems.add(optionalityFuture.get());
//                ferolReportItems.add(organicGrowthRunawayFuture.get());
//                ferolReportItems.add(topDogFuture.get());
//                ferolReportItems.add(operatingLeverageFuture.get());

                // Customers
//                ferolReportItems.add(acquisitionsFuture.get());
//                ferolReportItems.add(cyclicalityFuture.get());

                // Company specific factors
//                ferolReportItems.add(recurringRevenueFuture.get());
                ferolReportItems.add(pricingPowerFuture.get());

                sendSseEvent(sseEmitter, "Building and saving FEROL report...");
                FerolReport ferolReport = buildAndSaveReport(ticker, ferolReportItems);
                sendSseEvent(sseEmitter, "FEROL report built and saved.");

                // Send the final report
                sseEmitter.send(SseEmitter.event()
                        .name("COMPLETED")
                        .data(ferolReport, MediaType.APPLICATION_JSON));

                sseEmitter.complete();
                LOGGER.info("FEROL report generation complete for {}", ticker);

            } catch (Exception e) {
                LOGGER.error("Error generating FEROL report for {}: {}", ticker, e.getMessage(), e);
                sseEmitter.completeWithError(e);
            }
        }).start();

        return sseEmitter;
    }

    private void sendSseEvent(SseEmitter sseEmitter, String message) {
        try {
            sseEmitter.send(SseEmitter.event().name("MESSAGE").data(message));
            LOGGER.info("SSE Event sent: {}", message);
        } catch (Exception e) {
            LOGGER.error("Error sending SSE event: {}", e.getMessage());
            // Don't completeWithError here, as it might be a temporary network issue.
            // Let the main try-catch handle the completion if the core task fails.
        }
    }

    private void sendSseErrorEvent(SseEmitter sseEmitter, String message) {
        try {
            sseEmitter.send(SseEmitter.event().name("ERROR").data(message));
            LOGGER.error("SSE Error Event sent: {}", message);
        } catch (Exception e) {
            LOGGER.error("Error sending SSE error event: {}", e.getMessage());
        }
    }

    private FerolReport buildAndSaveReport(String ticker, List<FerolReportItem> ferolReportItems) {
        GeneratedReport generatedReport = generatedReportRepository.findBySymbol(ticker).orElse(new GeneratedReport());
        if (generatedReport.getSymbol()==null) {
            generatedReport.setSymbol(ticker);
        }

        FerolReport ferolReport = generatedReport.getFerolReport();
        if (ferolReport == null) {
            ferolReport = new FerolReport();
        }
        ferolReport.setItems(ferolReportItems);
        ferolReport.setGeneratedAt(LocalDateTime.now());
        generatedReport.setFerolReport(ferolReport);

        generatedReportRepository.save(generatedReport);

        return ferolReport;
    }

    private FerolReportItem calculateFinancialResilience(String ticker, Optional<IncomeStatementData> incomeStatementData, Optional<BalanceSheetData> balanceSheetData, SseEmitter sseEmitter) {
        final BigDecimal[] totalCashAndEquivalents = {BigDecimal.ZERO};
        final BigDecimal[] totalDebt = {BigDecimal.ZERO};
        final BigDecimal[] ttmEbitda = {BigDecimal.ZERO};
        final BigDecimal[] ttmInterestExpense = {BigDecimal.ZERO};

        balanceSheetData.ifPresent(balance -> {
            balance.getQuarterlyReports().stream()
                    .max(Comparator.comparing(report -> ((BalanceSheetReport) report).getDate()))
                    .ifPresent(latestBalanceSheet -> {
                        totalCashAndEquivalents[0] = safeParseBigDecimal(latestBalanceSheet.getCashAndCashEquivalents())
                                .add(safeParseBigDecimal(latestBalanceSheet.getShortTermInvestments()));
                        BigDecimal shortTermDebt = safeParseBigDecimal(latestBalanceSheet.getShortTermDebt());
                        BigDecimal longTermDebt = safeParseBigDecimal(latestBalanceSheet.getLongTermDebt());
                        totalDebt[0] = shortTermDebt.add(longTermDebt);
                    });
        });

        incomeStatementData.ifPresent(income -> {
            List<IncomeReport> quarterlyReports = income.getQuarterlyReports().stream()
                    .sorted(Comparator.comparing(report -> ((IncomeReport) report).getDate()).reversed())
                    .limit(4)
                    .collect(Collectors.toList());

            if (quarterlyReports.isEmpty()) {
                LOGGER.warn("No quarterly income reports found for ticker: {}", ticker);
                return;
            }

            for (IncomeReport report : quarterlyReports) {
                ttmEbitda[0] = ttmEbitda[0].add(safeParseBigDecimal(report.getOperatingIncome()).add(safeParseBigDecimal(report.getDepreciationAndAmortization())));
                ttmInterestExpense[0] = ttmInterestExpense[0].add(safeParseBigDecimal(report.getInterestExpense()));
            }
        });


        PromptTemplate promptTemplate = new PromptTemplate(financialResiliencePrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("totalCashAndEquivalents", totalCashAndEquivalents[0].toPlainString());
        promptParameters.put("totalDebt", totalDebt[0].toPlainString());
        promptParameters.put("ttmEbitda", ttmEbitda[0].toPlainString());
        promptParameters.put("ttmInterestExpense", ttmInterestExpense[0].toPlainString());
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            sendSseEvent(sseEmitter, "Sending data to LLM for resilience analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            sendSseEvent(sseEmitter, "Received LLM response for resilience analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("financialResilience", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateFinancialResilience' failed.";
            LOGGER.error(errorMessage, e);
            sendSseErrorEvent(sseEmitter, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    private FerolMoatAnalysisLlmResponse calculateMoats(String ticker,  Optional<SecFiling> secFilingData, Optional<CompanyOverview> companyOverview, SseEmitter sseEmitter) {

        StringBuilder stringBuilder = new StringBuilder();

        companyOverview.ifPresent( overview -> {
            stringBuilder.append(overview.getCompanyName());
            stringBuilder.append(overview.getDescription()).append("\n");
        });

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                sendSseEvent(sseEmitter, "No 10k available to get business description.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "No 10k available to get business description.");
        });


        PromptTemplate promptTemplate = new PromptTemplate(moatPrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolMoatAnalysisLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("business_description", stringBuilder.toString());

        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            sendSseEvent(sseEmitter, "Sending data to LLM for moat analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            sendSseEvent(sseEmitter, "Received LLM response for moat analysis.");
            FerolMoatAnalysisLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return convertedLlmResponse;
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateMoats' failed.";
            LOGGER.error(errorMessage, e);
            sendSseErrorEvent(sseEmitter, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    private FerolReportItem calculateGrossMargin(String ticker, SseEmitter sseEmitter) {
        sendSseEvent(sseEmitter, "Calculating Gross Margin...");
        Optional<FinancialRatiosData> financialRatiosData = financialRatiosRepository.findBySymbol(ticker);

        if (financialRatiosData.isEmpty() || financialRatiosData.get().getQuarterlyReports().isEmpty()) {
            LOGGER.warn("No financial ratios data found for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "Gross Margin calculation skipped: No data found.");
            return new FerolReportItem("grossMargin", 0, "No quarterly financial ratios data available.");
        }

        List<FinancialRatiosReport> quarterlyReports = financialRatiosData.get().getQuarterlyReports().stream()
                .sorted(Comparator.comparing(FinancialRatiosReport::getDate).reversed())
                .limit(4)
                .collect(Collectors.toList());

        if (quarterlyReports.size() < 4) {
            LOGGER.warn("Less than 4 quarterly reports found for gross margin calculation for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "Gross Margin calculation using " + quarterlyReports.size() + " quarters.");
        }

        BigDecimal sumGrossProfitMargin = BigDecimal.ZERO;
        int count = 0;
        for (FinancialRatiosReport report : quarterlyReports) {
            if (report.getGrossProfitMargin() != null) {
                sumGrossProfitMargin = sumGrossProfitMargin.add(report.getGrossProfitMargin());
                count++;
            }
        }

        if (count == 0) {
            sendSseEvent(sseEmitter, "Gross Margin calculation skipped: No gross profit margin data found in available reports.");
            return new FerolReportItem("grossMargin", 0, "No gross profit margin data available in the last " + quarterlyReports.size() + " quarterly reports.");
        }

        BigDecimal averageGrossProfitMargin = sumGrossProfitMargin.divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_HALF_UP);
        sendSseEvent(sseEmitter, "Average Gross Margin calculated: " + averageGrossProfitMargin.toPlainString() + "%");

        int score;
        String explanation;

        if (averageGrossProfitMargin.compareTo(BigDecimal.valueOf(0.50)) < 0) { // < 50%
            score = 1;
            explanation = "Average Gross Margin is less than 50% (" + averageGrossProfitMargin.multiply(BigDecimal.valueOf(100)).toPlainString() + "%), indicating lower profitability.";
        } else if (averageGrossProfitMargin.compareTo(BigDecimal.valueOf(0.80)) <= 0) { // 50% to 80%
            score = 2;
            explanation = "Average Gross Margin is between 50% and 80% (" + averageGrossProfitMargin.multiply(BigDecimal.valueOf(100)).toPlainString() + "%), indicating healthy profitability.";
        } else { // > 80%
            score = 3;
            explanation = "Average Gross Margin is greater than 80% (" + averageGrossProfitMargin.multiply(BigDecimal.valueOf(100)).toPlainString() + "%), indicating very strong profitability.";
        }

        return new FerolReportItem("grossMargin", score, explanation);
    }

    private FerolReportItem calculateReturnOnInvestedCapital(String ticker, SseEmitter sseEmitter) {
        sendSseEvent(sseEmitter, "Calculating Return on Invested Capital (ROIC)...");
        Optional<FinancialRatiosData> financialRatiosData = financialRatiosRepository.findBySymbol(ticker);

        if (financialRatiosData.isEmpty() || financialRatiosData.get().getAnnualReports().isEmpty() || financialRatiosData.get().getQuarterlyReports().isEmpty()) {
            LOGGER.warn("No financial ratios data found for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "ROIC calculation skipped: No data found.");
            return new FerolReportItem("roic", 0, "No annual or quarterly financial ratios data available.");
        }

        // Get annual ROIC for 5-year median
        List<BigDecimal> annualRoicValues = financialRatiosData.get().getAnnualReports().stream()
                .filter(report -> report.getRoic() != null)
                .sorted(Comparator.comparing(FinancialRatiosReport::getDate).reversed())
                .limit(5)
                .map(FinancialRatiosReport::getRoic)
                .collect(Collectors.toList());

        if (annualRoicValues.isEmpty()) {
            sendSseEvent(sseEmitter, "ROIC calculation skipped: No annual ROIC data found.");
            return new FerolReportItem("roic", 0, "No annual ROIC data available for median calculation.");
        }

        // Calculate 5-year median ROIC
        // Sort the list to find median
        Collections.sort(annualRoicValues);
        BigDecimal medianRoic;
        int middle = annualRoicValues.size() / 2;
        if (annualRoicValues.size() % 2 == 1) {
            medianRoic = annualRoicValues.get(middle);
        } else {
            medianRoic = (annualRoicValues.get(middle - 1).add(annualRoicValues.get(middle))).divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);
        }
        sendSseEvent(sseEmitter, "5-Year Median ROIC: " + medianRoic.toPlainString() + "%");


        // Get latest TTM ROIC (assuming the latest quarterly report's ROIC represents TTM or is close enough)
        // For accurate TTM, one would sum last 4 quarters' net income and divide by average invested capital,
        // but given the `roic` field in `FinancialRatiosReport` is already a single value, we'll use the latest.
        BigDecimal currentTtmRoic = BigDecimal.ZERO;
        Optional<FinancialRatiosReport> latestQuarterlyReport = financialRatiosData.get().getQuarterlyReports().stream()
                .filter(report -> report.getRoic() != null)
                .max(Comparator.comparing(FinancialRatiosReport::getDate));

        if (latestQuarterlyReport.isPresent()) {
            currentTtmRoic = latestQuarterlyReport.get().getRoic();
            sendSseEvent(sseEmitter, "Latest TTM ROIC: " + currentTtmRoic.toPlainString() + "%");
        } else {
            sendSseEvent(sseEmitter, "ROIC calculation partially skipped: No latest quarterly ROIC data for TTM.");
            return new FerolReportItem("roic", 0, "No latest quarterly ROIC data for TTM calculation.");
        }


        int score = 0;
        String explanation;
        BigDecimal roicPercentage = currentTtmRoic.multiply(BigDecimal.valueOf(100)); // Convert to percentage for comparison

        if (roicPercentage.compareTo(BigDecimal.valueOf(8)) < 0) { // ROIC < 8%
            score = 0;
            explanation = "ROIC is less than 8% (" + roicPercentage.toPlainString() + "%), indicating the company might be destroying value.";
        } else if (roicPercentage.compareTo(BigDecimal.valueOf(12)) < 0) { // ROIC 8% - 12%
            score = 1;
            explanation = "ROIC is between 8% and 12% (" + roicPercentage.toPlainString() + "%), indicating the company is essentially breaking even on its capital costs.";
        } else if (roicPercentage.compareTo(BigDecimal.valueOf(20)) < 0) { // ROIC 12% - 20%
            score = 2;
            explanation = "ROIC is between 12% and 20% (" + roicPercentage.toPlainString() + "%), indicating a solid compounder.";
        } else { // ROIC > 20%
            score = 3;
            explanation = "ROIC is greater than 20% (" + roicPercentage.toPlainString() + "%), indicating a strong competitive advantage.";
        }

        // Apply "Rising Rule"
        BigDecimal marginOfSafety = BigDecimal.valueOf(0.01); // 1% margin of safety
        BigDecimal medianRoicDecimal = medianRoic.divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP); // Convert median to decimal for comparison
        if (currentTtmRoic.compareTo(medianRoicDecimal.add(marginOfSafety)) > 0 && score < 3) {
            score++;
            explanation += " Additionally, ROIC is rising, suggesting an improving trend.";
        }
        sendSseEvent(sseEmitter, "ROIC calculation complete. Score: " + score);

        return new FerolReportItem("roic", score, explanation);
    }

    private FerolReportItem calculateFreeCashFlow(String ticker, SseEmitter sseEmitter) {
        sendSseEvent(sseEmitter, "Calculating Free Cash Flow (FCF)...");

        Optional<FinancialRatiosData> financialRatiosDataOptional = financialRatiosRepository.findBySymbol(ticker);
        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);

        if (financialRatiosDataOptional.isEmpty() || financialRatiosDataOptional.get().getQuarterlyReports().isEmpty() ||
            incomeStatementDataOptional.isEmpty() || incomeStatementDataOptional.get().getQuarterlyReports().isEmpty()) {
            LOGGER.warn("No sufficient data for FCF calculation for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "FCF calculation skipped: Insufficient data found.");
            return new FerolReportItem("freeCashFlow", 0, "Insufficient data for Free Cash Flow calculation.");
        }

        List<FinancialRatiosReport> financialRatiosReports = financialRatiosDataOptional.get().getQuarterlyReports();
        List<IncomeReport> incomeReports = incomeStatementDataOptional.get().getQuarterlyReports();

        // Sort reports by fiscal date ending in descending order
        financialRatiosReports.sort(Comparator.comparing(FinancialRatiosReport::getDate).reversed());
        incomeReports.sort(Comparator.comparing(IncomeReport::getDate).reversed());

        // Helper to get income report for a specific date
        Map<String, IncomeReport> incomeReportMap = incomeReports.stream()
                .collect(Collectors.toMap(IncomeReport::getDate, report -> report, (r1, r2) -> r1)); // Handle potential duplicates


        // Calculate TTM FCFs (Current and Previous Year)
        List<BigDecimal> currentTtmAdjustedFcfs = new ArrayList<>();
        List<BigDecimal> previousTtmAdjustedFcfs = new ArrayList<>();
        StringBuilder currentTtmQuarterlyDetails = new StringBuilder();
        StringBuilder previousTtmQuarterlyDetails = new StringBuilder();

        for (int i = 0; i < 8; i++) { // Need up to 8 quarters for current and previous TTM
            if (i < financialRatiosReports.size()) {
                FinancialRatiosReport fr = financialRatiosReports.get(i);
                IncomeReport ir = incomeReportMap.get(fr.getDate());

                if (fr.getFreeCashFlow() != null && ir != null && ir.getOperatingIncome() != null && ir.getDepreciationAndAmortization() != null) {
                    BigDecimal operatingIncome = safeParseBigDecimal(ir.getOperatingIncome());
                    BigDecimal depreciationAndAmortization = safeParseBigDecimal(ir.getDepreciationAndAmortization());

                    // EBITDA = Operating Income + Depreciation & Amortization
                    BigDecimal quarterlyEbitda = operatingIncome.add(depreciationAndAmortization);
                    // Stock-Based Compensation = EBITDA - Operating Income (as per user's definition)
                    BigDecimal stockBasedCompensation = quarterlyEbitda.subtract(operatingIncome);
                    // Adjusted FCF = FCF - SBC
                    BigDecimal adjustedFcf = fr.getFreeCashFlow().subtract(stockBasedCompensation);

                    if (i < 4) { // Current TTM
                        currentTtmAdjustedFcfs.add(adjustedFcf);
                        currentTtmQuarterlyDetails.append("Q").append(4 - i).append(": ").append(adjustedFcf.toPlainString()).append(" (FCF: ").append(fr.getFreeCashFlow().toPlainString()).append(", SBC: ").append(stockBasedCompensation.toPlainString()).append("); ");
                    } else { // Previous TTM
                        previousTtmAdjustedFcfs.add(adjustedFcf);
                        previousTtmQuarterlyDetails.append("Q").append(8 - i).append(": ").append(adjustedFcf.toPlainString()).append(" (FCF: ").append(fr.getFreeCashFlow().toPlainString()).append(", SBC: ").append(stockBasedCompensation.toPlainString()).append("); ");
                    }
                }
            }
        }

        if (currentTtmAdjustedFcfs.size() < 4) {
            sendSseEvent(sseEmitter, "FCF calculation partially skipped: Less than 4 quarters of data for current TTM FCF.");
            return new FerolReportItem("freeCashFlow", 0, "Less than 4 quarters of data for current TTM Free Cash Flow.");
        }

        BigDecimal currentTtmFcf = currentTtmAdjustedFcfs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        sendSseEvent(sseEmitter, "Current TTM Adjusted FCF: " + currentTtmFcf.toPlainString());

        int score;
        String explanation;

        // Build a detailed explanation string with numbers
        StringBuilder detailedExplanation = new StringBuilder();
        detailedExplanation.append("Current TTM Adjusted FCF: ").append(currentTtmFcf.toPlainString()).append(" (Quarterly breakdown: ").append(currentTtmQuarterlyDetails.toString()).append("). ");


        if (currentTtmFcf.compareTo(BigDecimal.ZERO) < 0) { // Negative FCF
            score = 0;
            explanation = "FCF is Negative, indicating the company is a 'Cash Burner'.";
        } else {
            // FCF is positive, now check growth
            if (previousTtmAdjustedFcfs.size() < 4) {
                // Not enough data for YoY growth, assume "Survivor" if positive but no growth data
                score = 1;
                explanation = "FCF is Positive, but insufficient data (less than 4 quarters for previous year) to assess growth, categorizing as 'Survivor'.";
            } else {
                BigDecimal previousTtmFcf = previousTtmAdjustedFcfs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                detailedExplanation.append("Previous TTM Adjusted FCF: ").append(previousTtmFcf.toPlainString()).append(" (Quarterly breakdown: ").append(previousTtmQuarterlyDetails.toString()).append("). ");
                sendSseEvent(sseEmitter, "Previous TTM Adjusted FCF: " + previousTtmFcf.toPlainString());

                if (previousTtmFcf.compareTo(BigDecimal.ZERO) <= 0) { // Avoid division by zero or negative growth from zero/negative
                     if (currentTtmFcf.compareTo(BigDecimal.ZERO) > 0) {
                         score = 1; // Positive FCF but previous was zero or negative, cannot calculate meaningful growth percentage directly
                         explanation = "FCF is Positive, but previous FCF was zero or negative, categorizing as 'Survivor'.";
                     } else { // Should not happen given outer if, but for completeness
                         score = 0;
                         explanation = "FCF is Negative, indicating a 'Cash Burner'.";
                     }
                } else {
                    BigDecimal growthPercentage = currentTtmFcf.subtract(previousTtmFcf)
                                                                .divide(previousTtmFcf, 4, BigDecimal.ROUND_HALF_UP)
                                                                .multiply(BigDecimal.valueOf(100));

                    detailedExplanation.append("YoY Growth: ").append(growthPercentage.toPlainString()).append("%. ");
                    sendSseEvent(sseEmitter, "FCF Growth (YoY): " + growthPercentage.toPlainString() + "%");

                    if (growthPercentage.compareTo(BigDecimal.valueOf(5)) < 0) { // 0% - 5% growth
                        score = 1;
                        explanation = "FCF is Positive with negligible growth, categorizing as 'Survivor'.";
                    } else if (growthPercentage.compareTo(BigDecimal.valueOf(15)) < 0) { // 5% - 15% growth
                        score = 2;
                        explanation = "FCF is Positive and growing steadily, categorizing as 'Compounder'.";
                    } else { // > 15% growth
                        score = 3;
                        explanation = "FCF is Positive and growing fast, categorizing as 'Cash Cow'.";
                    }
                }
            }
        }
        sendSseEvent(sseEmitter, "FCF calculation complete. Score: " + score);
        return new FerolReportItem("freeCashFlow", score, detailedExplanation.toString() + explanation);
    }

    private FerolReportItem calculateEarningsPerShare(String ticker, SseEmitter sseEmitter) {
        sendSseEvent(sseEmitter, "Calculating Earnings Per Share (EPS)...");

        Optional<EarningsHistory> earningsHistoryOptional = earningsHistoryRepository.findBySymbol(ticker);

        if (earningsHistoryOptional.isEmpty() || earningsHistoryOptional.get().getQuarterlyEarnings().size() < 8) {
            LOGGER.warn("No sufficient earnings history data for EPS calculation for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "EPS calculation skipped: Insufficient data found.");
            return new FerolReportItem("earningsPerShare", 0, "Insufficient quarterly earnings history data for EPS calculation (need at least 8 quarters).");
        }

        List<QuarterlyEarning> quarterlyEarnings = earningsHistoryOptional.get().getQuarterlyEarnings();
        // Sort by reportedDate in descending order to get latest first
        quarterlyEarnings.sort(Comparator.comparing(QuarterlyEarning::getReportedDate).reversed());

        // Get latest 8 quarters for current and previous TTM EPS
        List<QuarterlyEarning> relevantEarnings = quarterlyEarnings.stream().limit(8).collect(Collectors.toList());

        // Calculate current TTM EPS (latest 4 quarters)
        BigDecimal currentTtmEps = BigDecimal.ZERO;
        for (int i = 0; i < 4; i++) {
            currentTtmEps = currentTtmEps.add(safeParseBigDecimal(relevantEarnings.get(i).getReportedEPS()));
        }

        // Calculate previous TTM EPS (the 4 quarters before the current TTM)
        BigDecimal previousTtmEps = BigDecimal.ZERO;
        for (int i = 4; i < 8; i++) {
            previousTtmEps = previousTtmEps.add(safeParseBigDecimal(relevantEarnings.get(i).getReportedEPS()));
        }

        sendSseEvent(sseEmitter, "Current TTM EPS: " + currentTtmEps.toPlainString());
        sendSseEvent(sseEmitter, "Previous TTM EPS: " + previousTtmEps.toPlainString());

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
        sendSseEvent(sseEmitter, "EPS calculation complete. Score: " + score);
        return new FerolReportItem("earningsPerShare", score, detailedExplanation.toString() + explanation);
    }

    private FerolReportItem calculateOptionality(String ticker, SseEmitter sseEmitter) {
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        Optional<FinancialRatiosData> financialRatios = financialRatiosRepository.findBySymbol(ticker);
        Optional<IncomeStatementData> incomeStatementData = incomeStatementRepository.findBySymbol(ticker);

        StringBuilder stringBuilder = new StringBuilder();

        companyOverview.ifPresent( overview -> {
            stringBuilder.append(overview.getCompanyName());
            stringBuilder.append(overview.getDescription()).append("\n");
        });

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull( secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getManagementDiscussion());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
        });

        final String[] avgRdIntensity = {"N/A"};
        incomeStatementData.ifPresent(isData -> {
            List<IncomeReport> annualReports = isData.getAnnualReports();
            if (annualReports != null && annualReports.size() >= 3) {
                sendSseEvent(sseEmitter, "Calculating R&D intensity from last 3 annual reports...");
                List<BigDecimal> rdIntensities = new ArrayList<>();
                annualReports.stream()
                        .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                        .limit(3)
                        .forEach(report -> {
                            BigDecimal rd = safeParseBigDecimal(report.getResearchAndDevelopmentExpenses());
                            BigDecimal revenue = safeParseBigDecimal(report.getRevenue());
                            if (revenue.compareTo(BigDecimal.ZERO) != 0) {
                                rdIntensities.add(rd.divide(revenue, 4, BigDecimal.ROUND_HALF_UP));
                            }
                        });

                if (!rdIntensities.isEmpty()) {
                    BigDecimal sum = rdIntensities.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal avg = sum.divide(new BigDecimal(rdIntensities.size()), 4, BigDecimal.ROUND_HALF_UP);
                    avgRdIntensity[0] = avg.multiply(new BigDecimal("100")).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%";
                }

            } else {
                sendSseEvent(sseEmitter, "Not enough annual reports, calculating R&D intensity from last 6 quarterly reports...");
                List<IncomeReport> quarterlyReports = isData.getQuarterlyReports();
                if (quarterlyReports != null && !quarterlyReports.isEmpty()) {
                    List<BigDecimal> rdIntensities = new ArrayList<>();
                    quarterlyReports.stream()
                            .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                            .limit(6)
                            .forEach(report -> {
                                BigDecimal rd = safeParseBigDecimal(report.getResearchAndDevelopmentExpenses());
                                BigDecimal revenue = safeParseBigDecimal(report.getRevenue());
                                if (revenue.compareTo(BigDecimal.ZERO) != 0) {
                                    rdIntensities.add(rd.divide(revenue, 4, BigDecimal.ROUND_HALF_UP));
                                }
                            });

                    if (!rdIntensities.isEmpty()) {
                        BigDecimal sum = rdIntensities.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal avg = sum.divide(new BigDecimal(rdIntensities.size()), 4, BigDecimal.ROUND_HALF_UP);
                        avgRdIntensity[0] = avg.multiply(new BigDecimal("100")).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%";
                    }
                }
            }
            sendSseEvent(sseEmitter, "R&D intensity calculated: " + avgRdIntensity[0]);
        });

        var latestEarningsTranscript = getLatestEarningsTranscript(ticker);

        PromptTemplate promptTemplate = new PromptTemplate(optionalityPrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("business_description", stringBuilder.toString());
        promptParameters.put("latest_earnings_transcript", latestEarningsTranscript);
        financialRatios.ifPresentOrElse(financialRatiosData -> {
            var latestAnualReportRadios = financialRatiosData.getAnnualReports().stream()
                    .max(Comparator.comparing(tenKFiling -> tenKFiling.getDate()))
                    .get();
            promptParameters.put("free_cash_flow",latestAnualReportRadios.getFreeCashFlow());
            promptParameters.put("net_debt_ebitda",latestAnualReportRadios.getNetDebtToEbitda());

        },() -> {
            promptParameters.put("net_debt_ebitda", "Could not be determined");
            promptParameters.put("free_cash_flow", "Could not be determined");
        });
        promptParameters.put("avg_rd_intensity", avgRdIntensity[0]);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            sendSseEvent(sseEmitter, "Sending data to LLM for optionality analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            sendSseEvent(sseEmitter, "Received LLM response for optionality analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("optionality", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateOptionality' failed.";
            LOGGER.error(errorMessage, e);
            sendSseErrorEvent(sseEmitter, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    @NotNull
    private String getLatestEarningsTranscript(String ticker) {
        var latestQuarter = dateUtils.getDateQuarter(financialDataService.getLatestReportedDate(ticker));
        var latestEarningsTranscript = financialDataService.getEarningsCallTranscript(ticker, latestQuarter).block().getTranscript().stream()
                .map(t -> t.getSpeaker() + " (" + t.getTitle() + "): " + t.getContent() + " [" + t.getSentiment() + "]")
                .collect(Collectors.joining("\n"));
        return latestEarningsTranscript;
    }


    private FerolReportItem calculateOrganicGrowthRunaway(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        Optional<FinancialRatiosData> financialRatios = financialRatiosRepository.findBySymbol(ticker);

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
                sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
        });

        var latestEarningsTranscript = getLatestEarningsTranscript(ticker);
        ;

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
            sendSseEvent(sseEmitter, "Sending data to LLM for organic growth runaway analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            sendSseEvent(sseEmitter, "Received LLM response for organic growth runaway analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("organicGrowthRunway", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateOrganicGrowthRunaway' failed.";
            LOGGER.error(errorMessage, e);
            sendSseErrorEvent(sseEmitter, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    private FerolReportItem calculateTopDogOrFirstMover(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder stringBuilder = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                sendSseEvent(sseEmitter, "No 10k available to get business description.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "No 10k available to get business description.");
        });

        var latestEarningsTranscript = getLatestEarningsTranscript(ticker);
        ;

        PromptTemplate promptTemplate = new PromptTemplate(topDogPrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("business_description", stringBuilder.toString());
        promptParameters.put("latest_earnings_transcript", latestEarningsTranscript);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            sendSseEvent(sseEmitter, "Sending data to LLM for top dog or first mover analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            sendSseEvent(sseEmitter, "Received LLM response for top dog or first mover analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("topDogFirstMover", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateTopDogOrFirstMover' failed.";
            LOGGER.error(errorMessage, e);
            sendSseErrorEvent(sseEmitter, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }


    private FerolReportItem calculateOperatingLeverage(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder stringBuilder = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                sendSseEvent(sseEmitter, "No 10k available to get business description.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "No 10k available to get business description.");
        });

        String opexAsPercentageOfRevenueTrend = calculateOpexAsPercentageOfRevenueTrend3y(ticker)
                .stream()
                .map(BigDecimal::toPlainString).collect(Collectors.joining(", "));

        PromptTemplate promptTemplate = new PromptTemplate(operatingLeveragePrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("business_description", stringBuilder.toString());
        promptParameters.put("rev_cagr_3y", calculateRevenueCAGR3y(ticker));
        promptParameters.put("expected_rev_growth", calculateExpectedRevenueGrowth(ticker));
        promptParameters.put("opex_to_rev_list", opexAsPercentageOfRevenueTrend);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            sendSseEvent(sseEmitter, "Sending data to LLM for operating leverage analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            sendSseEvent(sseEmitter, "Received LLM response for operating leverage analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("operatingLeverage", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateOperatingLeverage' failed.";
            LOGGER.error(errorMessage, e);
            sendSseErrorEvent(sseEmitter, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }


    private FerolReportItem calculateAcquisitions(String ticker, SseEmitter sseEmitter) {
        var incomeDataOptional = incomeStatementRepository.findBySymbol(ticker);
        if (incomeDataOptional.isPresent()){
            var incomeData = incomeDataOptional.get();

            List<IncomeReport> reports = incomeData.getAnnualReports();

            if (reports == null || reports.isEmpty()) {
                String errorMessage = "No income annual reports found for symbol " + ticker;
                LOGGER.error(errorMessage);
                sendSseErrorEvent(sseEmitter, errorMessage);
            }

            // 1. Sort reports by date (Descending) to ensure we get the most recent ones first
            List<IncomeReport> sortedReports = reports.stream()
                    .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                    .limit(3) // We only care about the last 3 years
                    .collect(Collectors.toList());

            BigDecimal totalWeightedPercentage = BigDecimal.ZERO;
            int totalWeight = 0;

            // Weights: Most recent = 3, Next = 2, Last = 1
            int currentWeight = 3;

            for (IncomeReport report : sortedReports) {
                BigDecimal salesAndMarketExpense = new BigDecimal(report.getSellingAndMarketingExpenses());
                BigDecimal grossProfit = new BigDecimal(report.getGrossProfit());

                // 2. Fallback: If S&M is null or Zero, use SG&A
                if (salesAndMarketExpense == null || salesAndMarketExpense.compareTo(BigDecimal.ZERO) == 0) {
                    salesAndMarketExpense =  new BigDecimal(report.getSellingGeneralAndAdministrativeExpenses());
                    // Optional: You could log that you are using a proxy here
                }

                // Safety check: Skip if data is missing or Gross Profit is 0/Negative to avoid math errors
                if (salesAndMarketExpense == null || grossProfit == null || grossProfit.compareTo(BigDecimal.ZERO) <= 0) {
                    currentWeight--;
                    continue;
                }

                // Calculate ratio for this year: (S&M / GP) * 100
                BigDecimal yearRatio = salesAndMarketExpense.divide(grossProfit, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

                // Apply Weight
                totalWeightedPercentage = totalWeightedPercentage.add(yearRatio.multiply(new BigDecimal(currentWeight)));
                totalWeight += currentWeight;

                currentWeight--;
            }

            if (totalWeight == 0) {
                String errorMessage = "Data invalid for calculation of s&m % out of gross profit ";
                LOGGER.error(errorMessage);
                sendSseErrorEvent(sseEmitter, errorMessage);
            }

            // Final Calculation
            BigDecimal finalAverage = totalWeightedPercentage.divide(new BigDecimal(totalWeight), 2, RoundingMode.HALF_UP);

            // Calculate Score (0-5)
            int score = calculateAquisitionScore(finalAverage);

            return new FerolReportItem("customerAcquisition", score, "The average weighted (last year has highest weight) S&M % of Gross Profit for last 3 years is " + finalAverage.toPlainString() + "%");

        } else {
            String errorMessage = "Operation 'calculateAcquisitions' failed.";
            LOGGER.error(errorMessage + " No income data found for {}",ticker);
            sendSseErrorEvent(sseEmitter, errorMessage);
        }

        return new FerolReportItem("customerAcquisition", 0, "Something went wrong and score could not be calculated ");
    }

    private FerolReportItem calculateCyclicality(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new FerolReportItem("companyCyclicality", 0, "Something went wrong and score could not be calculated ");
        }
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder riskFactors = new StringBuilder();
        StringBuilder businessDescription = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            riskFactors.append(latestTenKFiling.getRiskFactors());
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                sendSseEvent(sseEmitter, "No 10k available to get risk factors.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            sendSseEvent(sseEmitter, "No 10k available to get risk factors.");
        });

        PromptTemplate promptTemplate = new PromptTemplate(companyCyclicalityPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);
        if (incomeStatementDataOptional.isEmpty()) {
            LOGGER.warn("No income statement data found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No income statement data found for ticker " + ticker);
        } else {
            var incomeStatementData = incomeStatementDataOptional.get();
            StringBuilder companyRevenueTrend = new StringBuilder();
            incomeStatementData.getAnnualReports().stream()
                    .sorted(Comparator.comparing(r -> LocalDate.parse(r.getDate())))
                    .forEachOrdered(r -> companyRevenueTrend.append(r.getRevenue()).append(" -> "));

            promptParameters.put("company_revenue_trend", companyRevenueTrend.toString());
        }

        Optional<FinancialRatiosData> financialRatiosDataOptional = financialRatiosRepository.findBySymbol(ticker);
        if (financialRatiosDataOptional.isEmpty()) {
            LOGGER.warn("No financial ratios data found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No financial ratios data found for ticker " + ticker);
        } else {
            var financialRatiosData = financialRatiosDataOptional.get();
            StringBuilder operatingMarginTrend = new StringBuilder();
            financialRatiosData.getAnnualReports().stream()
                    .sorted(Comparator.comparing(r -> LocalDate.parse(r.getDate())))
                    .forEachOrdered(r -> operatingMarginTrend.append(r.getOperatingProfitMargin()).append(" -> "));

            promptParameters.put("company_operating_margin_trend", operatingMarginTrend.toString());
        }

        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("company_industry", companyOverview.get().getIndustry());
        promptParameters.put("company_beta", companyOverview.get().getBeta());

        promptParameters.put("business_description", businessDescription.toString());
        promptParameters.put("risk_factors", riskFactors.toString());

        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            sendSseEvent(sseEmitter, "Sending data to LLM for company cyclicality analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            sendSseEvent(sseEmitter, "Received LLM response for company cyclicality analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("companyCyclicality", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateCyclicality' failed.";
            LOGGER.error(errorMessage, e);
            sendSseErrorEvent(sseEmitter, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    private FerolReportItem calculateRecurringRevenue(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new FerolReportItem("recurringRevenue", 0, "Something went wrong and score could not be calculated ");
        }
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder businessDescription = new StringBuilder();
        StringBuilder managementDiscussion = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            managementDiscussion.append(latestTenKFiling.getManagementDiscussion());
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
        });

        PromptTemplate promptTemplate = new PromptTemplate(recurringRevenuePrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        Optional<RevenueSegmentationData> revenueSegmentationOptional = revenueSegmentationDataRepository.findBySymbol(ticker);
        if (revenueSegmentationOptional.isEmpty()) {
            LOGGER.warn("No revenue segmentation data found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No revenue segmentation data found for ticker " + ticker);
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
            List<RevenueSegmentationReport> lastFiveYearsReports = revenueSegmentationData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getFiscalYear()))
                    .collect(Collectors.toList());

            promptParameters.put("revenue_segmentation", lastFiveYearsReports);
        }

        Optional<BalanceSheetData> balancesheetDataOptional = balanceSheetRepository.findBySymbol(ticker);
        if (balancesheetDataOptional.isEmpty()) {
            LOGGER.warn("No balance sheet data found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No balance sheet data found for ticker " + ticker);
        } else {
            var balancesheetData = balancesheetDataOptional.get();
            List<String> lastFiveQuarters = balancesheetData.getQuarterlyReports().stream()
                    .map(BalanceSheetReport::getDate)
                    .filter(Objects::nonNull)                    // safety
                    .distinct()                                  // remove duplicates
                    .sorted(Comparator.reverseOrder())          // newest first
                    .limit(5)                                    // take only 5
                    .sorted()                                    // optional: return in ascending order [2021,2022,2023,2024,2025]
                    .collect(Collectors.toList());

            List<String> lastQuartersDeferredRevenue= balancesheetData.getQuarterlyReports().stream()
                    .filter(r ->  lastFiveQuarters.contains(r.getDate()))
                    .map(BalanceSheetReport :: getDeferredRevenue)
                    .collect(Collectors.toList());

            promptParameters.put("deferredRevenue_last_5_quarters", lastQuartersDeferredRevenue);
        }

        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());

        promptParameters.put("business_description", businessDescription.toString());
        promptParameters.put("management_discussion", managementDiscussion.toString());

        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            sendSseEvent(sseEmitter, "Sending data to LLM for recurring revenue analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            sendSseEvent(sseEmitter, "Received LLM response for recurring revenue analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("recurringRevenue", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateRecurringRevenue' failed.";
            LOGGER.error(errorMessage, e);
            sendSseErrorEvent(sseEmitter, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    private FerolReportItem calculatePricingPower(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new FerolReportItem("pricingPower", 0, "Something went wrong and score could not be calculated ");
        }
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);
        StringBuilder businessDescription = new StringBuilder();
        StringBuilder managementDiscussion = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            managementDiscussion.append(latestTenKFiling.getManagementDiscussion());
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No 10k available to get data.");
        });

        PromptTemplate promptTemplate = new PromptTemplate(pricingPowerPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        StringBuilder financialTable = new StringBuilder();
        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);
        Optional<FinancialRatiosData> financialRatiosDataOptional = financialRatiosRepository.findBySymbol(ticker);
        if (incomeStatementDataOptional.isEmpty() || financialRatiosDataOptional.isEmpty()) {
            LOGGER.warn("No income statement data found for ticker: {}", ticker);
            sendSseErrorEvent(sseEmitter, "No income statement data found for ticker " + ticker);
        } else {
            var incomeStatementData = incomeStatementDataOptional.get();
            var financialRatiosData = financialRatiosDataOptional.get();

            List<String> lastFiveYears = incomeStatementData.getAnnualReports().stream()
                    .map(IncomeReport::getDate)
                    .filter(Objects::nonNull)                    // safety
                    .distinct()                                  // remove duplicates
                    .sorted(Comparator.reverseOrder())          // newest first
                    .limit(5)                                    // take only 5
                    .collect(Collectors.toList());

            lastFiveYears.forEach(year -> financialTable.append(year + " "));
            financialTable.append("\n");

            // Step 2: Filter the original stream to keep only reports from those years
            incomeStatementData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getDate()))
                    .forEach(incomeReport -> {
                        financialTable.append(incomeReport.getRevenue() + " ");
                    });

            financialTable.append("\n");
            incomeStatementData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getDate()))
                    .forEach(incomeReport -> {
                        financialTable.append(incomeReport.getSellingGeneralAndAdministrativeExpenses() + " ");
                    });

            financialTable.append("\n");
            financialRatiosData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getDate()))
                    .forEach(ratiosReport -> {
                        financialTable.append(ratiosReport.getGrossProfitMargin() + " ");
                    });

            financialTable.append("\n");
            financialRatiosData.getAnnualReports().stream()
                    .filter(r ->  lastFiveYears.contains(r.getDate()))
                    .forEach(ratiosReport -> {
                        financialTable.append(ratiosReport.getEbitdaMargin() + " ");
                    });

            promptParameters.put("financial_table", financialTable);
        }

        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(FerolLlmResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());

        promptParameters.put("business_description", businessDescription.toString());
        promptParameters.put("management_discussion", managementDiscussion.toString());
        promptParameters.put("latest_earnings_transcript", getLatestEarningsTranscript(ticker));

        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            sendSseEvent(sseEmitter, "Sending data to LLM for pricing power analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            sendSseEvent(sseEmitter, "Received LLM response for pricing power analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("pricingPower", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculatePricingPower' failed.";
            LOGGER.error(errorMessage, e);
            sendSseErrorEvent(sseEmitter, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    private BigDecimal calculateRevenueCAGRPerShare(String ticker) {
        Optional<IncomeStatementData> incomeStatementDataOpt = incomeStatementRepository.findBySymbol(ticker);
        Optional<SharesOutstandingData> sharesOutstandingDataOpt = sharesOutstandingRepository.findBySymbol(ticker);

        if (incomeStatementDataOpt.isEmpty() || incomeStatementDataOpt.get().getAnnualReports() == null || incomeStatementDataOpt.get().getAnnualReports().size() < 4) {
            LOGGER.warn("Not enough annual income reports for {}. Found {}.", ticker, incomeStatementDataOpt.map(d -> d.getAnnualReports().size()).orElse(0));
            return BigDecimal.ZERO;
        }
        if (sharesOutstandingDataOpt.isEmpty() || sharesOutstandingDataOpt.get().getData() == null || sharesOutstandingDataOpt.get().getData().isEmpty()) {
            LOGGER.warn("No shares outstanding data for {}", ticker);
            return BigDecimal.ZERO;
        }

        List<IncomeReport> annualReports = incomeStatementDataOpt.get().getAnnualReports().stream()
                .sorted(Comparator.comparing(IncomeReport::getDate))
                .collect(Collectors.toList());

        List<SharesOutstandingReport> sharesOutstandingReports = sharesOutstandingDataOpt.get().getData();
        sharesOutstandingReports.sort(Comparator.comparing(SharesOutstandingReport::getDate));

        IncomeReport latestReport = annualReports.get(annualReports.size() - 1);
        IncomeReport oldReport = annualReports.get(annualReports.size() - 4);

        BigDecimal latestRevenue = safeParseBigDecimal(latestReport.getRevenue());
        BigDecimal oldRevenue = safeParseBigDecimal(oldReport.getRevenue());

        SharesOutstandingReport latestSharesReport = findClosestSharesOutstanding(sharesOutstandingReports, latestReport.getDate());
        SharesOutstandingReport oldSharesReport = findClosestSharesOutstanding(sharesOutstandingReports, oldReport.getDate());

        if (latestSharesReport == null || oldSharesReport == null) {
            LOGGER.warn("Could not find matching shares outstanding data for the period for ticker: " + ticker);
            return BigDecimal.ZERO;
        }

        BigDecimal latestShares = safeParseBigDecimal(latestSharesReport.getSharesOutstandingDiluted());
        BigDecimal oldShares = safeParseBigDecimal(oldSharesReport.getSharesOutstandingDiluted());

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

    private double calculateExpectedRevenueGrowth(String ticker){
        Optional<IncomeStatementData> incomeStatementDataOpt = incomeStatementRepository.findBySymbol(ticker);
        List<IncomeReport> incomeReports = incomeStatementDataOpt.get().getAnnualReports();
        // Sort reports by fiscal date ending in descending order
        incomeReports.sort(Comparator.comparing(IncomeReport::getDate).reversed());
        IncomeReport lastAnualIncomeReport = incomeReports.getFirst();
        var lastYearRevenue = Double.parseDouble(lastAnualIncomeReport.getRevenue());

        var earningEstimates = earningsEstimatesRepository.findBySymbol(ticker);
        if (earningEstimates.isEmpty() || earningEstimates.get().getEstimates().isEmpty() || lastYearRevenue == 0){
            return 0.0;
        }
        List<Estimate> estimates = earningEstimates.get().getEstimates();

        // 1. Filter for valid dates and Sort by Date (Ascending) to find the NEAREST future estimate
        // We only want annual estimates (usually end in 12-31 or similar, but sorting finds the nearest)
        List<Estimate> sortedEstimates = estimates.stream()
                .filter(e -> e.getDate() != null && e.getRevenueEstimateAverage() != null)
                .sorted(Comparator.comparing(e -> LocalDate.parse(e.getDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
                .collect(Collectors.toList());

        Estimate targetEst = null;
        int estimateYear = 0;

        LocalDate lastIncomeReportFiscalDate = LocalDate.parse(lastAnualIncomeReport.getDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // Find the first estimate that is clearly in the future compared to last report
        for (Estimate e : sortedEstimates) {
            LocalDate d = LocalDate.parse(e.getDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (d.getYear() >  lastIncomeReportFiscalDate.getYear()) {
                targetEst = e;
                estimateYear = d.getYear();
                break; // Stop at the nearest one (FY1 or FY2)
            }
        }

        if (targetEst == null) {
            LOGGER.warn("No future estimates found.");
            return 0.0;
        }

        // 2. Calculate the Time Gap
        int yearsGap = estimateYear - lastIncomeReportFiscalDate.getYear();
        if (yearsGap < 1) yearsGap = 1; // Safety check

        // 3. Calculate CAGR
        // Formula: (Future / Past)^(1/n) - 1
        double futureRev = Double.parseDouble(targetEst.getRevenueEstimateAverage());

        double cagr = (Math.pow((futureRev / lastYearRevenue), 1.0 / yearsGap) - 1) * 100;

        return Math.round(cagr * 100.0) / 100.0;
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

        BigDecimal latestRevenue = safeParseBigDecimal(latestReport.getRevenue());
        BigDecimal oldRevenue = safeParseBigDecimal(oldReport.getRevenue());

        if (oldRevenue.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warn("Initial revenue was zero or negative, cannot calculate CAGR for ticker: " + ticker);
            return BigDecimal.ZERO;
        }

        double ratio = latestRevenue.divide(oldRevenue, 4, java.math.RoundingMode.HALF_UP).doubleValue();
        double cagr = Math.pow(ratio, 1.0 / 3.0) - 1.0;
        return BigDecimal.valueOf(cagr * 100).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private List<BigDecimal> calculateOpexAsPercentageOfRevenueTrend3y(String ticker) {
        Optional<IncomeStatementData> incomeStatementDataOpt = incomeStatementRepository.findBySymbol(ticker);

        if (incomeStatementDataOpt.isEmpty() || incomeStatementDataOpt.get().getAnnualReports() == null || incomeStatementDataOpt.get().getAnnualReports().size() < 3) {
            LOGGER.warn("Not enough annual income reports for {}. Found {}.", ticker, incomeStatementDataOpt.map(d -> d.getAnnualReports().size()).orElse(0));
            return Collections.emptyList();
        }

        List<IncomeReport> annualReports = incomeStatementDataOpt.get().getAnnualReports().stream()
                .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                .limit(3)
                .collect(Collectors.toList());

        if (annualReports.size() < 3) {
            LOGGER.warn("Not enough annual income reports for 3 year trend for {}. Found {}.", ticker, annualReports.size());
            return Collections.emptyList();
        }

        List<BigDecimal> opexPercentages = new ArrayList<>();
        for (IncomeReport report : annualReports) {
            BigDecimal sga = safeParseBigDecimal(report.getSellingGeneralAndAdministrativeExpenses());
            BigDecimal rd = safeParseBigDecimal(report.getResearchAndDevelopmentExpenses());
            BigDecimal totalRevenue = safeParseBigDecimal(report.getRevenue());

            if (totalRevenue.compareTo(BigDecimal.ZERO) == 0) {
                LOGGER.warn("Total revenue is zero for ticker: " + ticker + " for fiscal date: " + report.getDate());
                opexPercentages.add(BigDecimal.ZERO); // or handle as an error
            } else {
                BigDecimal opex = sga.add(rd);
                BigDecimal opexPercentage = opex.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                opexPercentages.add(opexPercentage.setScale(2, java.math.RoundingMode.HALF_UP));
            }
        }

        // The list is sorted from newest to oldest, let's reverse it to show trend over time
        Collections.reverse(opexPercentages);
        return opexPercentages;
    }

    private SharesOutstandingReport findClosestSharesOutstanding(List<SharesOutstandingReport> reports, String dateString) {
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

    public BigDecimal calculateSustainableGrowthRate(String ticker) {
        Optional<FinancialRatiosData> financialRatiosDataOpt = financialRatiosRepository.findBySymbol(ticker);

        if (financialRatiosDataOpt.isEmpty() || financialRatiosDataOpt.get().getAnnualReports() == null || financialRatiosDataOpt.get().getAnnualReports().size() < 3) {
            LOGGER.warn("Not enough annual financial ratios reports for SGR calculation for ticker: {}. Found {}.", ticker, financialRatiosDataOpt.map(d -> d.getAnnualReports().size()).orElse(0));
            return BigDecimal.ZERO;
        }

        List<FinancialRatiosReport> annualReports = financialRatiosDataOpt.get().getAnnualReports().stream()
                .sorted(Comparator.comparing(FinancialRatiosReport::getDate).reversed())
                .limit(3)
                .collect(Collectors.toList());

        if (annualReports.size() < 3) {
            LOGGER.warn("Not enough annual financial ratios reports for SGR calculation for ticker: {}. Found {}.", ticker, annualReports.size());
            return BigDecimal.ZERO;
        }

        List<BigDecimal> sgrValues = new ArrayList<>();
        for (FinancialRatiosReport report : annualReports) {
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

    private int calculateAquisitionScore(BigDecimal percentage) {
        double val = percentage.doubleValue();

        if (val < 10) return 5;       // Word of Mouth / Product Led Growth
        if (val < 20) return 4;       // Very Efficient
        if (val < 40) return 3;       // Normal / Benchmark
        if (val < 60) return 2;       // Getting Expensive
        if (val < 80) return 1;       // Expensive
        return 0;                     // Very Expensive / Burning Cash
    }
}
