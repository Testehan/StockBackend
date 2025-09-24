package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.BalanceSheetData;
import com.testehan.finana.model.BalanceSheetReport;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.model.IncomeStatementData;
import com.testehan.finana.model.llm.responses.FerolLlmResponse;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.FerolSseService;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FinancialResilienceCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialResilienceCalculator.class);

    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final LlmService llmService;
    private final FerolSseService ferolSseService;
    private final SafeParser safeParser;

    @Value("classpath:/prompts/financial_resilience_prompt.txt")
    private Resource financialResiliencePrompt;

    public FinancialResilienceCalculator(IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository, LlmService llmService, FerolSseService ferolSseService, SafeParser safeParser) {
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.llmService = llmService;
        this.ferolSseService = ferolSseService;
        this.safeParser = safeParser;
    }

    public FerolReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<IncomeStatementData> incomeStatementData = incomeStatementRepository.findBySymbol(ticker);
        Optional<BalanceSheetData> balanceSheetData = balanceSheetRepository.findBySymbol(ticker);

        final BigDecimal[] totalCashAndEquivalents = {BigDecimal.ZERO};
        final BigDecimal[] totalDebt = {BigDecimal.ZERO};
        final BigDecimal[] ttmEbitda = {BigDecimal.ZERO};
        final BigDecimal[] ttmInterestExpense = {BigDecimal.ZERO};

        balanceSheetData.ifPresent(balance -> {
            balance.getQuarterlyReports().stream()
                    .max(Comparator.comparing(report -> ((BalanceSheetReport) report).getDate()))
                    .ifPresent(latestBalanceSheet -> {
                        totalCashAndEquivalents[0] = safeParser.parse(latestBalanceSheet.getCashAndCashEquivalents())
                                .add(safeParser.parse(latestBalanceSheet.getShortTermInvestments()));
                        BigDecimal shortTermDebt = safeParser.parse(latestBalanceSheet.getShortTermDebt());
                        BigDecimal longTermDebt = safeParser.parse(latestBalanceSheet.getLongTermDebt());
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
                ttmEbitda[0] = ttmEbitda[0].add(safeParser.parse(report.getOperatingIncome()).add(safeParser.parse(report.getDepreciationAndAmortization())));
                ttmInterestExpense[0] = ttmInterestExpense[0].add(safeParser.parse(report.getInterestExpense()));
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
            ferolSseService.sendSseEvent(sseEmitter, "Sending data to LLM for resilience analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            ferolSseService.sendSseEvent(sseEmitter, "Received LLM response for resilience analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("financialResilience", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateFinancialResilience' failed.";
            LOGGER.error(errorMessage, e);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new FerolReportItem("financialResilience", -10, "Operation 'calculateFinancialResilience' failed.");
        }
    }
}
