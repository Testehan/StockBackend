package com.testehan.finana.service.reporting.calc;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.model.FinancialRatiosData;
import com.testehan.finana.model.IncomeReport;
import com.testehan.finana.model.IncomeStatementData;
import com.testehan.finana.model.SecFiling;
import com.testehan.finana.model.llm.responses.FerolLlmResponse;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.LlmService;
import com.testehan.finana.service.reporting.FerolSseService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OptionalityCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OptionalityCalculator.class);

    private final LlmService llmService;
    private final FerolSseService ferolSseService;
    private final SafeParser safeParser;
    private final DateUtils dateUtils;
    private final FinancialDataService financialDataService;


    @Value("classpath:/prompts/optionality_prompt.txt")
    private Resource optionalityPrompt;

    public OptionalityCalculator(LlmService llmService, FerolSseService ferolSseService, SafeParser safeParser, DateUtils dateUtils, FinancialDataService financialDataService) {
        this.llmService = llmService;
        this.ferolSseService = ferolSseService;
        this.safeParser = safeParser;
        this.dateUtils = dateUtils;
        this.financialDataService = financialDataService;
    }

    public FerolReportItem calculate(String ticker, Optional<SecFiling> secFilingData, Optional<CompanyOverview> companyOverview, Optional<FinancialRatiosData> financialRatios, Optional<IncomeStatementData> incomeStatementData, SseEmitter sseEmitter) {
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
                ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get management discussion.");
        });

        final String[] avgRdIntensity = {"N/A"};
        incomeStatementData.ifPresent(isData -> {
            List<IncomeReport> annualReports = isData.getAnnualReports();
            if (annualReports != null && annualReports.size() >= 3) {
                ferolSseService.sendSseEvent(sseEmitter, "Calculating R&D intensity from last 3 annual reports...");
                List<BigDecimal> rdIntensities = new ArrayList<>();
                annualReports.stream()
                        .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                        .limit(3)
                        .forEach(report -> {
                            BigDecimal rd = safeParser.parse(report.getResearchAndDevelopmentExpenses());
                            BigDecimal revenue = safeParser.parse(report.getRevenue());
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
                ferolSseService.sendSseEvent(sseEmitter, "Not enough annual reports, calculating R&D intensity from last 6 quarterly reports...");
                List<IncomeReport> quarterlyReports = isData.getQuarterlyReports();
                if (quarterlyReports != null && !quarterlyReports.isEmpty()) {
                    List<BigDecimal> rdIntensities = new ArrayList<>();
                    quarterlyReports.stream()
                            .sorted(Comparator.comparing(IncomeReport::getDate).reversed())
                            .limit(6)
                            .forEach(report -> {
                                BigDecimal rd = safeParser.parse(report.getResearchAndDevelopmentExpenses());
                                BigDecimal revenue = safeParser.parse(report.getRevenue());
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
            ferolSseService.sendSseEvent(sseEmitter, "R&D intensity calculated: " + avgRdIntensity[0]);
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
            ferolSseService.sendSseEvent(sseEmitter, "Sending data to LLM for optionality analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            ferolSseService.sendSseEvent(sseEmitter, "Received LLM response for optionality analysis.");
            FerolLlmResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new FerolReportItem("optionality", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateOptionality' failed.";
            LOGGER.error(errorMessage, e);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new FerolReportItem("optionality", -10, "Operation 'calculateOptionality' failed.");
        }
    }

    @NotNull
    public String getLatestEarningsTranscript(String ticker) {
        var latestQuarter = dateUtils.getDateQuarter(financialDataService.getLatestReportedDate(ticker));
        var latestEarningsTranscript = financialDataService.getEarningsCallTranscript(ticker, latestQuarter).block().getTranscript().stream()
                .map(t -> t.getSpeaker() + " (" + t.getTitle() + "): " + t.getContent() + " [" + t.getSentiment() + "]")
                .collect(Collectors.joining("\n"));
        return latestEarningsTranscript;
    }
}
