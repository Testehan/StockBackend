package com.testehan.finana.service;

import com.testehan.finana.util.DateUtils;
import org.springframework.stereotype.Service;

@Service
public class FinancialDataOrchestrator {
    private final FinancialDataService financialDataService;
    private final SecFilingService secFilingService;
    private final DateUtils dateUtils;

    public FinancialDataOrchestrator(FinancialDataService financialDataService, SecFilingService secFilingService, DateUtils dateUtils) {
        this.financialDataService = financialDataService;
        this.secFilingService = secFilingService;
        this.dateUtils = dateUtils;
    }

    public void ensureFinancialDataIsPresent(String ticker) {
        financialDataService.getLastStockQuote(ticker).block();
        financialDataService.getIndexQuotes("^GSPC").block();
        financialDataService.getIncomeStatements(ticker).block();
        financialDataService.getBalanceSheet(ticker).block();
        financialDataService.getCashFlow(ticker).block();
        financialDataService.getEarningsEstimates(ticker).block();
        financialDataService.getEarningsHistory(ticker).block();
        financialDataService.getCompanyOverview(ticker).block().get(0);
        financialDataService.getRevenueSegmentation(ticker).block();
        financialDataService.getRevenueGeographicSegmentation(ticker).block();
        secFilingService.fetchAndSaveSecFilings(ticker);
        secFilingService.getAndSaveSecFilings(ticker);

        financialDataService.getFinancialRatios(ticker);

        var latestReportDate = financialDataService.getLatestReportedDate(ticker);

        if (latestReportDate != null) {
            String dateQuarter = dateUtils.getDateQuarter(latestReportDate);
            financialDataService.getEarningsCallTranscript(ticker, dateQuarter).block();
        }
    }
}
