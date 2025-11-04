package com.testehan.finana.service;

import com.testehan.finana.model.FinancialDataAvailability;
import com.testehan.finana.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private final DateUtils dateUtils;

    public FinancialDataOrchestrator(CompanyDataService companyDataService, QuoteService quoteService, FinancialStatementService financialStatementService, EarningsService earningsService, SecFilingService secFilingService, FinancialDataService financialDataService, DateUtils dateUtils) {
        this.companyDataService = companyDataService;
        this.quoteService = quoteService;
        this.financialStatementService = financialStatementService;
        this.earningsService = earningsService;
        this.secFilingService = secFilingService;
        this.financialDataService = financialDataService; // For remaining methods
        this.dateUtils = dateUtils;
    }

    public void ensureFinancialDataIsPresent(String ticker) {
        quoteService.getLastStockQuote(ticker).block();
        quoteService.getIndexQuotes("^GSPC").block();
        financialStatementService.getIncomeStatements(ticker).block();
        financialStatementService.getBalanceSheet(ticker).block();
        financialStatementService.getCashFlow(ticker).block();
        earningsService.getEarningsEstimates(ticker).block();
        earningsService.getEarningsHistory(ticker).block();
        companyDataService.getCompanyOverview(ticker).block().get(0);
        financialStatementService.getRevenueSegmentation(ticker).block();
        financialStatementService.getRevenueGeographicSegmentation(ticker).block();
        secFilingService.fetchAndSaveSecFilings(ticker);
        secFilingService.getAndSaveSecFilings(ticker);

        financialDataService.getFinancialRatios(ticker);
        financialDataService.updateFinancialRatiosFromFmp(ticker);
        financialDataService.updateTtmFinancialRatios(ticker);

        var incomeData = financialStatementService.getIncomeStatements(ticker).block();
        var latestReportDate = incomeData.getQuarterlyReports().stream().max(Comparator.comparing(report -> dateUtils.parseDate(report.getDate(), formatter))).get().getDate();

        if (latestReportDate != null) {
            String dateQuarter = dateUtils.getDateQuarter(latestReportDate);
            earningsService.getEarningsCallTranscript(ticker, dateQuarter).block();
        }
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
        // FinancialRatiosRepository delete remains in FinancialDataService for now
        // GeneratedReportRepository delete remains in FinancialDataService for now
        financialStatementService.deleteRevenueGeographicSegmentationBySymbol(upperCaseSymbol);
        financialStatementService.deleteRevenueSegmentationBySymbol(upperCaseSymbol);
        secFilingService.deleteSecFilings(upperCaseSymbol);

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
