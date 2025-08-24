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
}
