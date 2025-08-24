package com.testehan.finana.service.reporting;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.LlmService;
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
import java.util.*;
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

    @Value("classpath:financial_resilience_prompt.txt")
    private Resource financialResiliencePrompt;


    public FerolService(BalanceSheetRepository balanceSheetRepository,
                        IncomeStatementRepository incomeStatementRepository,
                        LlmService llmService,
                        GeneratedReportRepository generatedReportRepository,
                        FinancialDataService financialDataService,
                        FinancialRatiosRepository financialRatiosRepository) {
        this.balanceSheetRepository = balanceSheetRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.llmService = llmService;
        this.generatedReportRepository = generatedReportRepository;
        this.financialDataService = financialDataService;
        this.financialRatiosRepository = financialRatiosRepository;
    }

    private BigDecimal safeParseBigDecimal(String value) {
        if (value == null || value.equalsIgnoreCase("None")) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    public SseEmitter getFerolReport(String ticker) {
        // Timeout set to 1 hour
        SseEmitter sseEmitter = new SseEmitter(3600000L);

        // Run the report generation in a separate thread to avoid blocking the main thread
        // and to allow the SseEmitter to send events asynchronously.
        new Thread(() -> {
            try {
                sendSseEvent(sseEmitter, "Initiating FEROL report generation for " + ticker + "...");

                sendSseEvent(sseEmitter, "Ensuring financial data is present...");
                financialDataService.ensureFinancialDataIsPresent(ticker);
                sendSseEvent(sseEmitter, "Financial data check complete.");

                sendSseEvent(sseEmitter, "Fetching income statement and balance sheet data...");
                Optional<IncomeStatementData> incomeStatementData = incomeStatementRepository.findBySymbol(ticker);
                Optional<BalanceSheetData> balanceSheetData = balanceSheetRepository.findBySymbol(ticker);
                sendSseEvent(sseEmitter, "Financial data retrieved.");

                sendSseEvent(sseEmitter, "Calculating financial resilience...");
                FerolReportItem financialResilience = calculateFinancialResilience(ticker, incomeStatementData, balanceSheetData, sseEmitter);
                sendSseEvent(sseEmitter, "Financial resilience calculation complete.");

                List<FerolReportItem> ferolReportItems = new ArrayList<>();
                ferolReportItems.add(financialResilience);

                sendSseEvent(sseEmitter, "Calculating Gross Margin...");
                FerolReportItem grossMargin = calculateGrossMargin(ticker, sseEmitter);
                ferolReportItems.add(grossMargin);
                sendSseEvent(sseEmitter, "Gross Margin calculation complete.");

                sendSseEvent(sseEmitter, "Calculating Return on Invested Capital (ROIC)...");
                FerolReportItem roic = calculateReturnOnInvestedCapital(ticker, sseEmitter);
                ferolReportItems.add(roic);
                sendSseEvent(sseEmitter, "Return on Invested Capital (ROIC) calculation complete.");

                sendSseEvent(sseEmitter, "Calculating Free Cash Flow...");
                FerolReportItem fcf = calculateFreeCashFlow(ticker, sseEmitter);
                ferolReportItems.add(fcf);
                sendSseEvent(sseEmitter, "Free Cash Flow calculation complete.");

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
                    .max(Comparator.comparing(report -> ((BalanceSheetReport) report).getFiscalDateEnding()))
                    .ifPresent(latestBalanceSheet -> {
                        totalCashAndEquivalents[0] = safeParseBigDecimal(latestBalanceSheet.getCashAndCashEquivalentsAtCarryingValue())
                                .add(safeParseBigDecimal(latestBalanceSheet.getShortTermInvestments()));
                        BigDecimal shortTermDebt = safeParseBigDecimal(latestBalanceSheet.getShortTermDebt());
                        BigDecimal longTermDebt = safeParseBigDecimal(latestBalanceSheet.getLongTermDebt());
                        totalDebt[0] = shortTermDebt.add(longTermDebt);
                    });
        });

        incomeStatementData.ifPresent(income -> {
            List<IncomeReport> quarterlyReports = income.getQuarterlyReports().stream()
                    .sorted(Comparator.comparing(report -> ((IncomeReport) report).getFiscalDateEnding()).reversed())
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

        sendSseEvent(sseEmitter, "Sending data to LLM for resilience analysis...");
        LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
        String llmResponse = llmService.callLlm(prompt);
        sendSseEvent(sseEmitter, "Received LLM response for resilience analysis.");
        FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

        return new FerolReportItem("financialResilience", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
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
                .sorted(Comparator.comparing(FinancialRatiosReport::getFiscalDateEnding).reversed())
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
                .sorted(Comparator.comparing(FinancialRatiosReport::getFiscalDateEnding).reversed())
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
                .max(Comparator.comparing(FinancialRatiosReport::getFiscalDateEnding));

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
        financialRatiosReports.sort(Comparator.comparing(FinancialRatiosReport::getFiscalDateEnding).reversed());
        incomeReports.sort(Comparator.comparing(IncomeReport::getFiscalDateEnding).reversed());

        // Helper to get income report for a specific date
        Map<String, IncomeReport> incomeReportMap = incomeReports.stream()
                .collect(Collectors.toMap(IncomeReport::getFiscalDateEnding, report -> report, (r1, r2) -> r1)); // Handle potential duplicates


        // Calculate TTM FCFs (Current and Previous Year)
        List<BigDecimal> currentTtmAdjustedFcfs = new ArrayList<>();
        List<BigDecimal> previousTtmAdjustedFcfs = new ArrayList<>();
        StringBuilder currentTtmQuarterlyDetails = new StringBuilder();
        StringBuilder previousTtmQuarterlyDetails = new StringBuilder();

        for (int i = 0; i < 8; i++) { // Need up to 8 quarters for current and previous TTM
            if (i < financialRatiosReports.size()) {
                FinancialRatiosReport fr = financialRatiosReports.get(i);
                IncomeReport ir = incomeReportMap.get(fr.getFiscalDateEnding());

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
}
