package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.model.IncomeStatementData;
import com.testehan.finana.model.SecFiling;
import com.testehan.finana.model.llm.responses.TAMScoreExplanationResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.repository.SecFilingRepository;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TamCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TamCalculator.class);

    @Value("classpath:/prompts/100Bagger/tam_prompt.txt")
    private Resource tamPrompt;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final SecFilingRepository secFilingRepository;

    private final ChecklistSseService checklistSseService;
    private final LlmService llmService;
    private final SafeParser safeParser;

    public TamCalculator(CompanyOverviewRepository companyOverviewRepository, IncomeStatementRepository incomeStatementRepository, SecFilingRepository secFilingRepository, ChecklistSseService checklistSseService, LlmService llmService, SafeParser safeParser) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.secFilingRepository = secFilingRepository;
        this.checklistSseService = checklistSseService;
        this.llmService = llmService;
        this.safeParser = safeParser;
    }

    public TAMScoreExplanationResponse calculate(String ticker, SseEmitter sseEmitter) {

        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        if (companyOverview.isEmpty()){
            LOGGER.warn("No Company overview found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No Company overview found for ticker " + ticker);
            return new TAMScoreExplanationResponse(-10, "Something went wrong and score could not be calculated ");
        }

        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);

        StringBuilder businessDescription = new StringBuilder();
        StringBuilder managementDiscussion = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            businessDescription.append(latestTenKFiling.getBusinessDescription());
                            managementDiscussion.append(latestTenKFiling.getManagementDiscussion());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                checklistSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            checklistSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
        });

        Optional<IncomeStatementData> incomeStatementDataOptional = incomeStatementRepository.findBySymbol(ticker);
        if (incomeStatementDataOptional.isEmpty()) {
            LOGGER.warn("No income statement data found for ticker: {}", ticker);
            checklistSseService.sendSseErrorEvent(sseEmitter, "No income statement data found for ticker " + ticker);
            return new TAMScoreExplanationResponse(-10, "Something went wrong and score could not be calculated ");
        }

        BigDecimal[] ttmRevenue = getTtmRevenue(ticker, incomeStatementDataOptional);

        PromptTemplate promptTemplate = new PromptTemplate(tamPrompt);
        Map<String, Object> promptParameters = new HashMap<>();

        var llmResponseOutputConverter = new BeanOutputConverter<>(TAMScoreExplanationResponse.class);

        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("ttm_revenue", ttmRevenue[0].toPlainString());
        promptParameters.put("business_description", businessDescription);
        promptParameters.put("mda", managementDiscussion);
        promptParameters.put("format", llmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            checklistSseService.sendSseEvent(sseEmitter, "Sending data to LLM for TAM analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            checklistSseService.sendSseEvent(sseEmitter, "Received LLM response with TAM analysis.");
             return llmResponseOutputConverter.convert(llmResponse);

        } catch (Exception e) {
            String errorMessage = "Operation 'calculatetotalAddressableMarket' failed.";
            LOGGER.error(errorMessage, e);
            checklistSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new TAMScoreExplanationResponse(-10, "Operation 'calculatetotalAddressableMarket' failed.");
        }
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
}
