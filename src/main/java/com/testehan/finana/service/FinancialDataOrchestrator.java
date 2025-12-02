package com.testehan.finana.service;

import com.testehan.finana.model.FinancialDataAvailability;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;

@Service
public class FinancialDataOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialDataOrchestrator.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CompanyDataService companyDataService;
    private final QuoteService quoteService;
    private final FinancialStatementService financialStatementService;
    private final EarningsService earningsService;
    private final SecFilingService secFilingService;
    private final FinancialDataService financialDataService; // Temporarily keep for ratios and update methods
    private final AdjustmentService adjustmentService;

    private final DateUtils dateUtils;

    public FinancialDataOrchestrator(CompanyDataService companyDataService, QuoteService quoteService, FinancialStatementService financialStatementService, EarningsService earningsService, SecFilingService secFilingService, FinancialDataService financialDataService, AdjustmentService adjustmentService, DateUtils dateUtils) {
        this.companyDataService = companyDataService;
        this.quoteService = quoteService;
        this.financialStatementService = financialStatementService;
        this.earningsService = earningsService;
        this.secFilingService = secFilingService;
        this.financialDataService = financialDataService; // For remaining methods
        this.adjustmentService = adjustmentService;
        this.dateUtils = dateUtils;
    }

    public Mono<Void> ensureFinancialDataIsPresent(String ticker) {
        // TRACK A: Independent data (Start these immediately)
        Mono<Void> independentTrack = Mono.when(
                quoteService.getLastStockQuote(ticker),
                quoteService.getIndexQuotes("^GSPC"),
                earningsService.getEarningsEstimates(ticker),
                earningsService.getEarningsHistory(ticker),
                companyDataService.getCompanyOverview(ticker),
                secFilingService.fetchAndSaveSecFilings(ticker),
                secFilingService.getAndSaveSecFilings(ticker)
        );

        // TRACK B: The Dependency Chain
        // 1. First, fetch and save the core financials
        Mono<IncomeStatementData> incomeStatementShared = financialStatementService
                .getIncomeStatements(ticker)
                .cache();

        Mono<Void> coreFinancials = Mono.when(
                incomeStatementShared,
                financialStatementService.getBalanceSheet(ticker),
                financialStatementService.getCashFlow(ticker),
                financialStatementService.getRevenueSegmentation(ticker),
                financialStatementService.getRevenueGeographicSegmentation(ticker)
        );

        // 2. Once core financials are saved, run Ratios and Transcripts and Adjustments
        Mono<Void> dependentData = coreFinancials.then(Mono.defer(() -> {
            // This block only starts after coreFinancials completes (writes to DB are done)

            Mono<Void> transcriptMono = incomeStatementShared.flatMap(incomeData -> incomeData.getQuarterlyReports().stream()
                    .max(Comparator.comparing(report -> dateUtils.parseDate(report.getDate(), formatter)))
                    .map(report -> earningsService.getEarningsCallTranscript(ticker, dateUtils.getDateQuarter(report.getDate())).then())
                    .orElse(Mono.just(true).then()));

            Mono<Void> ratiosMono = financialDataService.getFinancialRatios(ticker)
                    .then()
                    .onErrorResume(e -> {
                        LOGGER.error("Ratio calculation failed for " + ticker, e);
                        return Mono.empty();
                    });

            Mono<Void> adjustmentsMono = adjustmentService.getFinancialAdjustments(ticker)
                    .then();

            return Mono.when(transcriptMono, ratiosMono, adjustmentsMono);
        }));

        // FINAL JOIN: Run Track A and Track B in parallel
        return Mono.when(independentTrack, dependentData);
    }

    public void deleteFinancialData(String symbol) {
        String upperCaseSymbol = symbol.toUpperCase();

        financialStatementService.deleteBalanceSheetBySymbol(upperCaseSymbol);
        financialStatementService.deleteCashFlowBySymbol(upperCaseSymbol);
        earningsService.deleteCompanyEarningsTranscriptsBySymbol(upperCaseSymbol);
        companyDataService.deleteBySymbol(upperCaseSymbol);
        financialStatementService.deleteIncomeStatementsBySymbol(upperCaseSymbol);
        earningsService.deleteEarningsHistoryBySymbol(upperCaseSymbol);
        quoteService.deleteBySymbol(upperCaseSymbol);
        earningsService.deleteEarningsEstimatesBySymbol(upperCaseSymbol);
        financialStatementService.deleteRevenueGeographicSegmentationBySymbol(upperCaseSymbol);
        financialStatementService.deleteRevenueSegmentationBySymbol(upperCaseSymbol);
        secFilingService.deleteSecFilings(upperCaseSymbol);
        adjustmentService.deleteFinancialAdjustmentBySymbol(upperCaseSymbol);

        LOGGER.info("Deleted all financial data for ticker: {}", upperCaseSymbol);
    }

    public String getLatestReportedDate(String ticker) {
        var incomeData = financialStatementService.getIncomeStatements(ticker).block();
        var latestIncomeReport = incomeData.getQuarterlyReports().stream().max(Comparator.comparing(report -> dateUtils.parseDate(report.getDate(), formatter))).get();
        return latestIncomeReport.getDate();
    }

    public FinancialDataAvailability checkFinancialDataAvailability(String ticker) {
        FinancialDataAvailability availability = new FinancialDataAvailability();
        String upperCaseTicker = ticker.toUpperCase();

        availability.setLastStockQuote(quoteService.hasStockQuotes(upperCaseTicker));
        availability.setIncomeStatements(financialStatementService.hasIncomeStatements(upperCaseTicker));
        availability.setBalanceSheet(financialStatementService.hasBalanceSheet(upperCaseTicker));
        availability.setCashFlow(financialStatementService.hasCashFlow(upperCaseTicker));
        availability.setEarningsEstimates(earningsService.hasEarningsEstimates(upperCaseTicker));
        availability.setEarningsHistory(earningsService.hasEarningsHistory(upperCaseTicker));
        availability.setCompanyOverview(companyDataService.hasCompanyOverview(upperCaseTicker));

        availability.setRevenueSegmentation(financialStatementService.hasRevenueSegmentation(upperCaseTicker));
        availability.setRevenueGeographicSegmentation(financialStatementService.hasRevenueGeographicSegmentation(upperCaseTicker));
        // The following two are still in FinancialDataService
        availability.setFinancialRatios(financialDataService.hasFinancialRatios(upperCaseTicker));

        availability.setEarningsCallTranscript(earningsService.hasEarningsCallTranscript(upperCaseTicker));
        availability.setSecQuarterlyFilings(secFilingService.hasTenQFilings(upperCaseTicker));
        availability.setSecAnnualFilings(secFilingService.hasTenKFilings(upperCaseTicker));

        return availability;
    }
}