package com.testehan.finana.service.reporting;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FerolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FerolService.class);

    private final BalanceSheetRepository balanceSheetRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final LlmService llmService;
    private final GeneratedReportRepository generatedReportRepository;
    private final FinancialDataService financialDataService;

    @Value("classpath:financial_resilience_prompt.txt")
    private Resource financialResiliencePrompt;


    public FerolService(BalanceSheetRepository balanceSheetRepository,
                        IncomeStatementRepository incomeStatementRepository,
                        LlmService llmService,
                        GeneratedReportRepository generatedReportRepository,
                        FinancialDataService financialDataService) {
        this.balanceSheetRepository = balanceSheetRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.llmService = llmService;
        this.generatedReportRepository = generatedReportRepository;
        this.financialDataService = financialDataService;
    }

    private BigDecimal safeParseBigDecimal(String value) {
        if (value == null || value.equalsIgnoreCase("None")) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    public FerolLlmResponse getFerolReport(String ticker) {
        financialDataService.ensureFinancialDataIsPresent(ticker);

        final BigDecimal[] totalCashAndEquivalents = {BigDecimal.ZERO};
        final BigDecimal[] totalDebt = {BigDecimal.ZERO};
        final BigDecimal[] ttmEbitda = {BigDecimal.ZERO};
        final BigDecimal[] ttmInterestExpense = {BigDecimal.ZERO};

        // Fetch BalanceSheetData for the most recent quarter
        balanceSheetRepository.findBySymbol(ticker).ifPresent(balanceSheetData -> {
            balanceSheetData.getQuarterlyReports().stream()
                    .max(Comparator.comparing(report -> ((BalanceSheetReport) report).getFiscalDateEnding()))
                    .ifPresent(latestBalanceSheet -> {
                        totalCashAndEquivalents[0] = safeParseBigDecimal(latestBalanceSheet.getCashAndCashEquivalentsAtCarryingValue())
                                .add(safeParseBigDecimal(latestBalanceSheet.getShortTermInvestments()));
                        BigDecimal shortTermDebt = safeParseBigDecimal(latestBalanceSheet.getShortTermDebt());
                        BigDecimal longTermDebt = safeParseBigDecimal(latestBalanceSheet.getLongTermDebt());
                        totalDebt[0] = shortTermDebt.add(longTermDebt);
                    });
        });

        // Fetch IncomeStatementData for the trailing 12 months
        incomeStatementRepository.findBySymbol(ticker).ifPresent(incomeStatementData -> {
            List<IncomeReport> quarterlyReports = incomeStatementData.getQuarterlyReports().stream()
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

        LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
        String llmResponse = llmService.callLlm(prompt);
        FerolLlmResponse ferolLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

        GeneratedReport generatedReport = new GeneratedReport();
        generatedReport.setSymbol(ticker);
        FerolReport ferolReport = new FerolReport();
        FerolReportItem ferolReportItem = new FerolReportItem();
        ferolReportItem.setName("financialResilience");
        ferolReportItem.setScore(ferolLlmResponse.getScore());
        ferolReportItem.setExplanation(ferolLlmResponse.getExplanation());
        ferolReport.getItems().add(ferolReportItem);
        generatedReport.setFerolReport(ferolReport);

        generatedReportRepository.save(generatedReport);

        return ferolLlmResponse;
    }
}
