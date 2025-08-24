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

    public FerolReport getFerolReport(String ticker) {
        financialDataService.ensureFinancialDataIsPresent(ticker);

        Optional<IncomeStatementData> incomeStatementData = incomeStatementRepository.findBySymbol(ticker);
        Optional<BalanceSheetData> balanceSheetData = balanceSheetRepository.findBySymbol(ticker);

        FerolReportItem financialResilience = calculateFinancialResilience(ticker, incomeStatementData, balanceSheetData);

        List<FerolReportItem> ferolReportItems = new ArrayList<>();
        ferolReportItems.add(financialResilience);

        return buildAndSaveReport(ticker, ferolReportItems);
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

    private FerolReportItem calculateFinancialResilience(String ticker, Optional<IncomeStatementData> incomeStatementData, Optional<BalanceSheetData> balanceSheetData) {
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

        LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
        String llmResponse = llmService.callLlm(prompt);
        FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

        return new FerolReportItem("financialResilience", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
    }
}
