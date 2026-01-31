package com.testehan.finana.service;

import com.testehan.finana.model.FinancialDataAvailability;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.finstatement.IncomeReport;
import com.testehan.finana.util.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancialDataOrchestratorTest {

    private FinancialDataOrchestrator orchestrator;

    @Mock private CompanyDataService companyDataService;
    @Mock private QuoteService quoteService;
    @Mock private FinancialStatementService financialStatementService;
    @Mock private EarningsService earningsService;
    @Mock private SecFilingService secFilingService;
    @Mock private FinancialDataService financialDataService;
    @Mock private AdjustmentService adjustmentService;
    @Mock private DateUtils dateUtils;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        orchestrator = new FinancialDataOrchestrator(
                companyDataService, quoteService, financialStatementService,
                earningsService, secFilingService, financialDataService,
                adjustmentService, dateUtils
        );
    }

    @Test
    void ensureFinancialDataIsPresent_callsAllServices() {
        String ticker = "AAPL";

        // Mocks for independent track
        when(quoteService.getLastStockQuote(ticker)).thenReturn(Mono.empty());
        when(quoteService.getIndexQuotes(anyString())).thenReturn(Mono.empty());
        when(earningsService.getEarningsEstimates(ticker)).thenReturn(Mono.empty());
        when(earningsService.getEarningsHistory(ticker)).thenReturn(Mono.empty());
        when(companyDataService.getCompanyOverview(ticker)).thenReturn(Mono.empty());
        when(secFilingService.fetchAndSaveSecFilings(ticker)).thenReturn(Mono.empty());
        when(secFilingService.getAndSaveSecFilings(ticker)).thenReturn(Mono.empty());

        // Mocks for core financials
        IncomeStatementData incomeData = new IncomeStatementData();
        IncomeReport report = new IncomeReport();
        report.setDate("2023-01-01");
        incomeData.setQuarterlyReports(List.of(report));
        
        when(financialStatementService.getIncomeStatements(ticker)).thenReturn(Mono.just(incomeData));
        when(financialStatementService.getBalanceSheet(ticker)).thenReturn(Mono.empty());
        when(financialStatementService.getCashFlow(ticker)).thenReturn(Mono.empty());
        when(financialStatementService.getRevenueSegmentation(ticker)).thenReturn(Mono.empty());
        when(financialStatementService.getRevenueGeographicSegmentation(ticker)).thenReturn(Mono.empty());

        // Mocks for dependent data
        when(dateUtils.parseDate(anyString(), any(DateTimeFormatter.class))).thenReturn(java.time.LocalDate.of(2023, 1, 1));
        when(dateUtils.getDateQuarter(anyString())).thenReturn("Q1-2023");
        when(earningsService.getEarningsCallTranscript(ticker, "Q1-2023")).thenReturn(Mono.empty());
        when(financialDataService.getFinancialRatios(ticker)).thenReturn(Mono.empty());
        when(adjustmentService.getFinancialAdjustments(ticker)).thenReturn(Mono.empty());

        Mono<Void> result = orchestrator.ensureFinancialDataIsPresent(ticker);

        StepVerifier.create(result)
                .verifyComplete();

        verify(quoteService).getLastStockQuote(ticker);
        verify(financialStatementService).getIncomeStatements(ticker);
        verify(earningsService).getEarningsCallTranscript(ticker, "Q1-2023");
        verify(financialDataService).getFinancialRatios(ticker);
    }

    @Test
    void checkFinancialDataAvailability_returnsCorrectAvailability() {
        String ticker = "AAPL";
        
        when(quoteService.hasStockQuotes(ticker)).thenReturn(true);
        when(financialStatementService.hasIncomeStatements(ticker)).thenReturn(true);
        when(financialStatementService.hasBalanceSheet(ticker)).thenReturn(false);
        // ... set other mocks if needed

        FinancialDataAvailability availability = orchestrator.checkFinancialDataAvailability(ticker);

        assertTrue(availability.isLastStockQuote());
        assertTrue(availability.isIncomeStatements());
        assertFalse(availability.isBalanceSheet());
        
        verify(quoteService).hasStockQuotes(ticker);
    }

    @Test
    void deleteFinancialData_callsDeleteOnAllServices() {
        String ticker = "AAPL";

        orchestrator.deleteFinancialData(ticker);

        verify(financialStatementService).deleteBalanceSheetBySymbol(ticker);
        verify(financialStatementService).deleteCashFlowBySymbol(ticker);
        verify(earningsService).deleteCompanyEarningsTranscriptsBySymbol(ticker);
        verify(companyDataService).deleteBySymbol(ticker);
        verify(financialStatementService).deleteIncomeStatementsBySymbol(ticker);
        verify(earningsService).deleteEarningsHistoryBySymbol(ticker);
        verify(quoteService).deleteBySymbol(ticker);
        verify(earningsService).deleteEarningsEstimatesBySymbol(ticker);
        verify(financialStatementService).deleteRevenueGeographicSegmentationBySymbol(ticker);
        verify(financialStatementService).deleteRevenueSegmentationBySymbol(ticker);
        verify(secFilingService).deleteSecFilings(ticker);
        verify(adjustmentService).deleteFinancialAdjustmentBySymbol(ticker);
    }
}
