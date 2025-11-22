package com.testehan.finana.service;

import com.testehan.finana.model.adjustment.FinancialAdjustment;
import com.testehan.finana.model.adjustment.FinancialAdjustmentReport;
import com.testehan.finana.model.finstatement.*;
import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.repository.*;
import com.testehan.finana.util.SafeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdjustmentServiceImplTest {

    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private FinancialAdjustmentRepository financialAdjustmentRepository;
    @Mock
    private BalanceSheetRepository balanceSheetRepository;
    @Mock
    private CashFlowRepository cashFlowRepository;
    @Mock
    private CompanyDataService companyDataService;
    @Mock
    private QuoteService quoteService;
    @Mock
    private FinancialRatiosRepository financialRatiosRepository;

    private SafeParser safeParser;
    private AdjustmentServiceImpl adjustmentService;

    @BeforeEach
    void setUp() {
        safeParser = new SafeParser();
        adjustmentService = new AdjustmentServiceImpl(
                incomeStatementRepository,
                financialAdjustmentRepository,
                balanceSheetRepository,
                cashFlowRepository,
                companyDataService,
                quoteService,
                financialRatiosRepository,
                safeParser
        );
    }

    @Test
    void getFinancialAdjustments_NewSymbol_Success() {
        String symbol = "AAPL";
        BigDecimal price = new BigDecimal("150.00");

        // Mock QuoteService
        GlobalQuote quote = new GlobalQuote();
        quote.setPrice("150.00");
        when(quoteService.getLastStockQuote(symbol)).thenReturn(Mono.just(quote));

        // Mock Repositories returning empty for existing adjustment
        when(financialAdjustmentRepository.findBySymbol(symbol)).thenReturn(Optional.empty());

        // Mock Financial Data
        IncomeStatementData incomeData = new IncomeStatementData();
        List<IncomeReport> annualIncomeReports = createMockIncomeReports(symbol);
        incomeData.setAnnualReports(annualIncomeReports);
        when(incomeStatementRepository.findBySymbol(symbol)).thenReturn(Optional.of(incomeData));

        BalanceSheetData bsData = new BalanceSheetData();
        List<BalanceSheetReport> annualBsReports = createMockBsReports(symbol);
        bsData.setAnnualReports(annualBsReports);
        when(balanceSheetRepository.findBySymbol(symbol)).thenReturn(Optional.of(bsData));

        CashFlowData cfData = new CashFlowData();
        List<CashFlowReport> annualCfReports = createMockCfReports(symbol);
        cfData.setAnnualReports(annualCfReports);
        when(cashFlowRepository.findBySymbol(symbol)).thenReturn(Optional.of(cfData));

        FinancialRatiosData ratioData = new FinancialRatiosData();
        List<FinancialRatiosReport> annualRatioReports = createMockRatioReports(symbol);
        ratioData.setAnnualReports(annualRatioReports);
        when(financialRatiosRepository.findBySymbol(symbol)).thenReturn(Optional.of(ratioData));

        com.testehan.finana.model.CompanyOverview overview = new com.testehan.finana.model.CompanyOverview();
        overview.setMarketCap("2500000000000");
        when(companyDataService.getCompanyOverview(symbol)).thenReturn(Mono.just(List.of(overview)));

        when(financialAdjustmentRepository.save(any(FinancialAdjustment.class))).thenAnswer(i -> i.getArguments()[0]);

        // Execute
        FinancialAdjustment result = adjustmentService.getFinancialAdjustments(symbol);

        // Verify
        assertNotNull(result);
        assertEquals(symbol, result.getSymbol());
        assertFalse(result.getAnnualAdjustments().isEmpty());
        
        // Check if multiple years are calculated (we have 9 mock income reports, so 9-4=5 adjustments possible)
        assertTrue(result.getAnnualAdjustments().size() >= 1);
        
        verify(financialAdjustmentRepository).save(any(FinancialAdjustment.class));
    }

    @Test
    void getFinancialAdjustments_ExistingAdjustment_SmartRefresh() {
        String symbol = "AAPL";
        BigDecimal newPrice = new BigDecimal("160.00");

        // Mock QuoteService
        GlobalQuote quote = new GlobalQuote();
        quote.setPrice("160.00");
        lenient().when(quoteService.getLastStockQuote(symbol)).thenReturn(Mono.just(quote));

        // Mock existing adjustment from 2024
        FinancialAdjustment existing = new FinancialAdjustment();
        existing.setSymbol(symbol);
        FinancialAdjustmentReport report = new FinancialAdjustmentReport();
        report.setDate("2024-12-31");
        report.setAdjustedEps("5.00");
        report.setReportedEps("4.50");
        report.setWeightedAverageShsOutDil("15000000000");
        report.setTotalDebt("100000000000");
        report.setCashAndCashEquivalents("50000000000");
        report.setAdjustedEbitda("100000000000");
        report.setReportedEbitda("90000000000");
        report.setAdjustedBookValueOfEquity("200000000000");
        report.setReportedBookValueOfEquity("180000000000");
        
        List<FinancialAdjustmentReport> reports = new ArrayList<>();
        reports.add(report);
        existing.setAnnualAdjustments(reports);
        
        lenient().when(financialAdjustmentRepository.findBySymbol(symbol)).thenReturn(Optional.of(existing));

        // Mock repositories to return data up to 2024 - No Refresh needed
        IncomeStatementData incomeData = new IncomeStatementData();
        List<IncomeReport> annualIncomeReports = createMockIncomeReports(symbol); // includes 2024
        incomeData.setAnnualReports(annualIncomeReports);
        lenient().when(incomeStatementRepository.findBySymbol(symbol)).thenReturn(Optional.of(incomeData));

        // These mocks are necessary for the getFinancialAdjustments method to proceed
        BalanceSheetData bsData = new BalanceSheetData();
        bsData.setAnnualReports(createMockBsReports(symbol));
        when(balanceSheetRepository.findBySymbol(symbol)).thenReturn(Optional.of(bsData));

        CashFlowData cfData = new CashFlowData();
        cfData.setAnnualReports(createMockCfReports(symbol));
        when(cashFlowRepository.findBySymbol(symbol)).thenReturn(Optional.of(cfData));

        FinancialRatiosData ratioData = new FinancialRatiosData();
        ratioData.setAnnualReports(createMockRatioReports(symbol));
        when(financialRatiosRepository.findBySymbol(symbol)).thenReturn(Optional.of(ratioData));

        com.testehan.finana.model.CompanyOverview overview = new com.testehan.finana.model.CompanyOverview();
        overview.setMarketCap("2500000000000");
        when(companyDataService.getCompanyOverview(symbol)).thenReturn(Mono.just(List.of(overview)));

        // Execute
        FinancialAdjustment result = adjustmentService.getFinancialAdjustments(symbol);

        // Verify
        assertNotNull(result.getAnnualAdjustments());
        assertFalse(result.getAnnualAdjustments().isEmpty());
        assertEquals("32.0000", result.getAnnualAdjustments().get(0).getAdjustedPe()); // 160 / 5.00
        verify(financialAdjustmentRepository).save(existing);
    }

    @Test
    void getFinancialAdjustments_IncompleteData_ReturnsEmpty() {
        String symbol = "AAPL";
        // This test checks the early-exit condition when financial data is missing.
        lenient().when(financialAdjustmentRepository.findBySymbol(symbol)).thenReturn(Optional.empty());
        lenient().when(incomeStatementRepository.findBySymbol(symbol)).thenReturn(Optional.empty());
        lenient().when(companyDataService.getCompanyOverview(symbol)).thenReturn(Mono.empty());
        lenient().when(quoteService.getLastStockQuote(symbol)).thenReturn(Mono.empty());


        FinancialAdjustment result = adjustmentService.getFinancialAdjustments(symbol);
        assertNull(result.getSymbol());
    }

    // Helper methods for mocking data
    private List<IncomeReport> createMockIncomeReports(String symbol) {
        List<IncomeReport> reports = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            IncomeReport r = new IncomeReport();
            r.setDate((2024 - i) + "-12-31");
            r.setResearchAndDevelopmentExpenses("1000000000");
            r.setOperatingIncome("5000000000");
            r.setRevenue("20000000000");
            r.setIncomeTaxExpense("1000000000");
            r.setIncomeBeforeTax("4000000000");
            r.setNetIncome("3000000000");
            r.setEpsDiluted("2.00");
            r.setWeightedAverageShsOutDil("1500000000");
            r.setOtherExpenses("0");
            r.setSellingAndMarketingExpenses("2000000000");
            reports.add(r);
        }
        return reports;
    }

    private List<BalanceSheetReport> createMockBsReports(String symbol) {
        List<BalanceSheetReport> reports = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            BalanceSheetReport r = new BalanceSheetReport();
            r.setDate((2024 - i) + "-12-31");
            r.setTotalDebt("10000000000");
            r.setTotalEquity("20000000000");
            r.setCashAndCashEquivalents("5000000000");
            r.setTotalStockholdersEquity("20000000000");
            reports.add(r);
        }
        return reports;
    }

    private List<CashFlowReport> createMockCfReports(String symbol) {
        List<CashFlowReport> reports = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            CashFlowReport r = new CashFlowReport();
            r.setDate((2024 - i) + "-12-31");
            r.setStockBasedCompensation("500000000");
            r.setOperatingCashFlow("4000000000");
            r.setCapitalExpenditure("1000000000");
            r.setFreeCashFlow("3000000000");
            reports.add(r);
        }
        return reports;
    }

    private List<FinancialRatiosReport> createMockRatioReports(String symbol) {
        List<FinancialRatiosReport> reports = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            FinancialRatiosReport r = new FinancialRatiosReport();
            r.setDate((2024 - i) + "-12-31");
            r.setRoic(new BigDecimal("0.15"));
            r.setPeRatio(new BigDecimal("20"));
            r.setNetDebtToEbitda(new BigDecimal("1.0"));
            r.setSalesToCapitalRatio(new BigDecimal("2.0"));
            r.setInterestCoverageRatio(new BigDecimal("5.0"));
            r.setEnterpriseValueMultiple(new BigDecimal("10.0"));
            reports.add(r);
        }
        return reports;
    }
}
