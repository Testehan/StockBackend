package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.*;
import com.testehan.finana.model.llm.responses.LlmScoreExplanationResponse;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.EarningsEstimatesRepository;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OperatingLeverageCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperatingLeverageCalculator.class);

    private final CompanyOverviewRepository companyOverviewRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final EarningsEstimatesRepository earningsEstimatesRepository;
    private final SecFilingRepository secFilingRepository;
    private final LlmService llmService;
    private final ChecklistSseService ferolSseService;

    private final SafeParser safeParser;

    @Value("classpath:/prompts/operating_leverage_prompt.txt")
    private Resource operatingLeveragePrompt;

    public OperatingLeverageCalculator(CompanyOverviewRepository companyOverviewRepository, IncomeStatementRepository incomeStatementRepository, EarningsEstimatesRepository earningsEstimatesRepository, SecFilingRepository secFilingRepository, LlmService llmService, ChecklistSseService ferolSseService, SafeParser safeParser) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.earningsEstimatesRepository = earningsEstimatesRepository;
        this.secFilingRepository = secFilingRepository;
        this.llmService = llmService;
        this.ferolSseService = ferolSseService;
        this.safeParser = safeParser;
    }

    public ReportItem calculate(String ticker, SseEmitter sseEmitter) {
        Optional<CompanyOverview> companyOverview = companyOverviewRepository.findBySymbol(ticker);
        Optional<SecFiling> secFilingData = secFilingRepository.findBySymbol(ticker);

        String opexAsPercentageOfRevenueTrend = calculateOpexAsPercentageOfRevenueTrend3y(ticker)
                .stream()
                .map(BigDecimal::toPlainString).collect(Collectors.joining(", "));
        BigDecimal revenueCAGR3y = calculateRevenueCAGR3y(ticker);
        double expectedRevenueGrowth = calculateExpectedRevenueGrowth(ticker);

        StringBuilder stringBuilder = new StringBuilder();

        secFilingData.ifPresentOrElse(secData -> {
            if (Objects.nonNull(secData.getTenKFilings()) && !secData.getTenKFilings().isEmpty()) {
                secData.getTenKFilings().stream().max(Comparator.comparing(tenKFiling -> tenKFiling.getFiledAt()))
                        .ifPresent(latestTenKFiling -> {
                            stringBuilder.append(latestTenKFiling.getBusinessDescription());
                        });
            } else {
                LOGGER.warn("No 10k found for ticker: {}", ticker);
                ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
            }
        }, () -> {
            LOGGER.warn("No 10k found for ticker: {}", ticker);
            ferolSseService.sendSseEvent(sseEmitter, "No 10k available to get business description.");
        });

        PromptTemplate promptTemplate = new PromptTemplate(operatingLeveragePrompt);
        var ferolLlmResponseOutputConverter = new BeanOutputConverter<>(LlmScoreExplanationResponse.class);

        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("company_name", companyOverview.get().getCompanyName());
        promptParameters.put("business_description", stringBuilder.toString());
        promptParameters.put("rev_cagr_3y", revenueCAGR3y);
        promptParameters.put("expected_rev_growth", expectedRevenueGrowth);
        promptParameters.put("opex_to_rev_list", opexAsPercentageOfRevenueTrend);
        promptParameters.put("format", ferolLlmResponseOutputConverter.getFormat());
        Prompt prompt = promptTemplate.create(promptParameters);

        try {
            ferolSseService.sendSseEvent(sseEmitter, "Sending data to LLM for operating leverage analysis...");
            LOGGER.info("Calling LLM with prompt for {}: {}", ticker, prompt);
            String llmResponse = llmService.callLlm(prompt);
            ferolSseService.sendSseEvent(sseEmitter, "Received LLM response for operating leverage analysis.");
            LlmScoreExplanationResponse convertedLlmResponse = ferolLlmResponseOutputConverter.convert(llmResponse);

            return new ReportItem("operatingLeverage", convertedLlmResponse.getScore(), convertedLlmResponse.getExplanation());
        } catch (Exception e) {
            String errorMessage = "Operation 'calculateOperatingLeverage' failed.";
            LOGGER.error(errorMessage, e);
            ferolSseService.sendSseErrorEvent(sseEmitter, errorMessage);
            return new ReportItem("operatingLeverage", -10, "Operation 'calculateOperatingLeverage' failed.");
        }
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
                .filter(e -> e.getDate() != null && e.getRevenueAvg() != null)
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
        double futureRev = Double.parseDouble(targetEst.getRevenueAvg());

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
            BigDecimal sga = safeParser.parse(report.getSellingGeneralAndAdministrativeExpenses());
            BigDecimal rd = safeParser.parse(report.getResearchAndDevelopmentExpenses());
            BigDecimal totalRevenue = safeParser.parse(report.getRevenue());

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
}
