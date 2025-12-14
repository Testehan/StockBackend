package com.testehan.finana.service.mcp;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.adjustment.FinancialAdjustmentReport;
import com.testehan.finana.model.filing.QuarterlyEarningsTranscript;
import com.testehan.finana.model.filing.TenKFilings;
import com.testehan.finana.model.filing.TenQFilings;
import com.testehan.finana.model.finstatement.*;
import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.repository.CompanyEarningsTranscriptsRepository;
import com.testehan.finana.repository.FinancialAdjustmentRepository;
import com.testehan.finana.repository.SecFilingRepository;
import com.testehan.finana.service.*;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockDataTools {

    private final QuoteService quoteService;
    private final FinancialStatementService financialStatementService;
    private final CompanyDataService companyDataService;
    private final FinancialDataService financialDataService;
    private final EarningsService earningsService;
    private final CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository;
    private final FinancialAdjustmentRepository financialAdjustmentRepository;
    private final SecFilingRepository secFilingRepository;

    public StockDataTools(QuoteService quoteService,
                          FinancialStatementService financialStatementService,
                          CompanyDataService companyDataService,
                          FinancialDataService financialDataService,
                          EarningsService earningsService,
                          CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository,
                          FinancialAdjustmentRepository financialAdjustmentRepository,
                          SecFilingRepository secFilingRepository) {
        this.quoteService = quoteService;
        this.financialStatementService = financialStatementService;
        this.companyDataService = companyDataService;
        this.financialDataService = financialDataService;
        this.earningsService = earningsService;
        this.companyEarningsTranscriptsRepository = companyEarningsTranscriptsRepository;
        this.financialAdjustmentRepository = financialAdjustmentRepository;
        this.secFilingRepository = secFilingRepository;
    }

    @SuppressWarnings("unchecked")
    private void setTicker(String ticker, ToolContext toolContext) {
        var tickerHolder = (java.util.Map<String, String>) toolContext.getContext().get("ticker_holder");
        if (tickerHolder != null) {
            tickerHolder.put("ticker", ticker);
        }
    }

    // ============== QUOTE ==============

    @Tool(name = "get_stock_quote", description = "Get current stock price, volume, and basic quote data for a ticker symbol")
    public GlobalQuote getQuote(String ticker, ToolContext toolContext) {
        setTicker(ticker, toolContext);
        return quoteService.getLastStockQuote(ticker).block();
    }

    // ============== COMPANY OVERVIEW ==============

    @Tool(name = "get_company_overview", description = "Get company overview information including name, sector, industry, market cap, description, CEO, employee count, etc.")
    public List<CompanyOverview> getCompanyOverview(String ticker, ToolContext toolContext) {
        setTicker(ticker, toolContext);
        return companyDataService.getCompanyOverview(ticker).block();
    }

    // ============== FINANCIAL RATIOS ==============

    @Tool(name = "get_financial_ratios_ttm", description = "Get trailing twelve months (TTM) financial ratios including P/E, P/B, profit margins, ROE, ROA, debt ratios, EV/EBITDA, etc.")
    public FinancialRatiosReport getFinancialRatiosTtm(String ticker, ToolContext toolContext) {
        setTicker(ticker, toolContext);
        var ratios = financialDataService.getFinancialRatios(ticker).block();
        return ratios != null && ratios.isPresent() ? ratios.get().getTtmReport() : null;
    }

    @Tool(name = "get_financial_ratios_annual", description = "Get annual financial ratios by year including P/E, P/B, profit margins, ROE, ROA, debt ratios, EV/EBITDA, etc. If year is provided, returns only that year. If no year provided, returns all available years.")
    public List<FinancialRatiosReport> getFinancialRatiosAnnual(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023). If not provided, returns all available years.") String year,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var ratios = financialDataService.getFinancialRatios(ticker).block();
        if (ratios == null || !ratios.isPresent()) return null;
        
        List<FinancialRatiosReport> allReports = ratios.get().getAnnualReports();
        if (year == null || year.isBlank()) return allReports;
        
        return allReports.stream()
                .filter(r -> r.getDate() != null && r.getDate().startsWith(year))
                .toList();
    }

    @Tool(name = "get_financial_ratios_quarterly", description = "Get quarterly financial ratios including P/E, P/B, profit margins, ROE, ROA, debt ratios, EV/EBITDA, etc. If year and quarter are provided, returns only that quarter. If no filters provided, returns all available quarters.")
    public List<FinancialRatiosReport> getFinancialRatiosQuarterly(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023).") String year,
            @ToolParam(description = "Optional: specific quarter (e.g., Q1, Q2, Q3, Q4).") String quarter,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var ratios = financialDataService.getFinancialRatios(ticker).block();
        if (ratios == null || !ratios.isPresent()) return null;
        
        List<FinancialRatiosReport> allReports = ratios.get().getQuarterlyReports();
        
        if (year == null || year.isBlank()) {
            if (quarter == null || quarter.isBlank()) {
                return allReports;
            }
            return allReports.stream()
                    .filter(r -> r.getDate() != null && r.getDate().contains(quarter))
                    .toList();
        }
        
        if (quarter == null || quarter.isBlank()) {
            return allReports.stream()
                    .filter(r -> r.getDate() != null && r.getDate().startsWith(year))
                    .toList();
        }
        
        return allReports.stream()
                .filter(r -> r.getDate() != null && r.getDate().startsWith(year))
                .filter(r -> r.getDate() != null && r.getDate().contains(quarter))
                .toList();
    }

    // ============== INCOME STATEMENT ==============

    @Tool(name = "get_income_statement_annual", description = "Get annual income statement data including revenue, net income, EPS, operating income, interest expense, etc. If year is provided, returns only that year. If no year provided, returns all available years.")
    public List<IncomeReport> getIncomeStatementAnnual(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023). If not provided, returns all available years.") String year,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var data = financialStatementService.getIncomeStatements(ticker).block();
        if (data == null) return null;
        
        List<IncomeReport> allReports = data.getAnnualReports();
        if (year == null || year.isBlank()) return allReports;
        
        return allReports.stream()
                .filter(r -> r.getFiscalYear() != null && r.getFiscalYear().equals(year))
                .toList();
    }

    @Tool(name = "get_income_statement_quarterly", description = "Get quarterly income statement data including revenue, net income, EPS, operating income, interest expense, etc. If year and quarter are provided, returns only that quarter. If no filters provided, returns all available quarters.")
    public List<IncomeReport> getIncomeStatementQuarterly(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023).") String year,
            @ToolParam(description = "Optional: specific quarter (e.g., Q1, Q2, Q3, Q4).") String quarter,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var data = financialStatementService.getIncomeStatements(ticker).block();
        if (data == null) return null;
        
        List<IncomeReport> allReports = data.getQuarterlyReports();
        
        if (year == null || year.isBlank()) {
            if (quarter == null || quarter.isBlank()) {
                return allReports;
            }
            return allReports.stream()
                    .filter(r -> r.getPeriod() != null && r.getPeriod().contains(quarter))
                    .toList();
        }
        
        if (quarter == null || quarter.isBlank()) {
            return allReports.stream()
                    .filter(r -> r.getFiscalYear() != null && r.getFiscalYear().equals(year))
                    .toList();
        }
        
        return allReports.stream()
                .filter(r -> r.getFiscalYear() != null && r.getFiscalYear().equals(year))
                .filter(r -> r.getPeriod() != null && r.getPeriod().contains(quarter))
                .toList();
    }

    // ============== BALANCE SHEET ==============

    @Tool(name = "get_balance_sheet_annual", description = "Get annual balance sheet data including total assets, total liabilities, total equity, cash, debt, inventory, etc. If year is provided, returns only that year. If no year provided, returns all available years.")
    public List<BalanceSheetReport> getBalanceSheetAnnual(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023). If not provided, returns all available years.") String year,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var data = financialStatementService.getBalanceSheet(ticker).block();
        if (data == null) return null;
        
        List<BalanceSheetReport> allReports = data.getAnnualReports();
        if (year == null || year.isBlank()) return allReports;
        
        return allReports.stream()
                .filter(r -> r.getFiscalYear() != null && r.getFiscalYear().equals(year))
                .toList();
    }

    @Tool(name = "get_balance_sheet_quarterly", description = "Get quarterly balance sheet data including total assets, total liabilities, total equity, cash, debt, inventory, etc. If year and quarter are provided, returns only that quarter. If no filters provided, returns all available quarters.")
    public List<BalanceSheetReport> getBalanceSheetQuarterly(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023).") String year,
            @ToolParam(description = "Optional: specific quarter (e.g., Q1, Q2, Q3, Q4).") String quarter,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var data = financialStatementService.getBalanceSheet(ticker).block();
        if (data == null) return null;
        
        List<BalanceSheetReport> allReports = data.getQuarterlyReports();
        
        if (year == null || year.isBlank()) {
            if (quarter == null || quarter.isBlank()) {
                return allReports;
            }
            return allReports.stream()
                    .filter(r -> r.getPeriod() != null && r.getPeriod().contains(quarter))
                    .toList();
        }
        
        if (quarter == null || quarter.isBlank()) {
            return allReports.stream()
                    .filter(r -> r.getFiscalYear() != null && r.getFiscalYear().equals(year))
                    .toList();
        }
        
        return allReports.stream()
                .filter(r -> r.getFiscalYear() != null && r.getFiscalYear().equals(year))
                .filter(r -> r.getPeriod() != null && r.getPeriod().contains(quarter))
                .toList();
    }

    // ============== CASH FLOW ==============

    @Tool(name = "get_cash_flow_annual", description = "Get annual cash flow data including operating cash flow, investing cash flow, financing cash flow, free cash flow, capital expenditures, etc. If year is provided, returns only that year. If no year provided, returns all available years.")
    public List<CashFlowReport> getCashFlowAnnual(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023). If not provided, returns all available years.") String year,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var data = financialStatementService.getCashFlow(ticker).block();
        if (data == null) return null;
        
        List<CashFlowReport> allReports = data.getAnnualReports();
        if (year == null || year.isBlank()) return allReports;
        
        return allReports.stream()
                .filter(r -> r.getFiscalYear() != null && r.getFiscalYear().equals(year))
                .toList();
    }

    @Tool(name = "get_cash_flow_quarterly", description = "Get quarterly cash flow data including operating cash flow, investing cash flow, financing cash flow, free cash flow, capital expenditures, etc. If year and quarter are provided, returns only that quarter. If no filters provided, returns all available quarters.")
    public List<CashFlowReport> getCashFlowQuarterly(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023).") String year,
            @ToolParam(description = "Optional: specific quarter (e.g., Q1, Q2, Q3, Q4).") String quarter,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var data = financialStatementService.getCashFlow(ticker).block();
        if (data == null) return null;
        
        List<CashFlowReport> allReports = data.getQuarterlyReports();
        
        if (year == null || year.isBlank()) {
            if (quarter == null || quarter.isBlank()) {
                return allReports;
            }
            return allReports.stream()
                    .filter(r -> r.getPeriod() != null && r.getPeriod().contains(quarter))
                    .toList();
        }
        
        if (quarter == null || quarter.isBlank()) {
            return allReports.stream()
                    .filter(r -> r.getFiscalYear() != null && r.getFiscalYear().equals(year))
                    .toList();
        }
        
        return allReports.stream()
                .filter(r -> r.getFiscalYear() != null && r.getFiscalYear().equals(year))
                .filter(r -> r.getPeriod() != null && r.getPeriod().contains(quarter))
                .toList();
    }

    // ============== REVENUE SEGMENTATION ==============

    @Tool(name = "get_revenue_segmentation_annual", description = "Get annual revenue breakdown by product or service segment. If year is provided, returns only that year. If no year provided, returns all available years.")
    public List<RevenueSegmentationReport> getRevenueSegmentationAnnual(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023). If not provided, returns all available years.") String year,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var data = financialStatementService.getRevenueSegmentation(ticker).block();
        if (data == null) return null;
        
        List<RevenueSegmentationReport> allReports = data.getAnnualReports();
        if (year == null || year.isBlank()) return allReports;
        
        try {
            int yearInt = Integer.parseInt(year);
            return allReports.stream()
                    .filter(r -> r.getFiscalYear() == yearInt)
                    .toList();
        } catch (NumberFormatException e) {
            return allReports;
        }
    }

    @Tool(name = "get_revenue_segmentation_quarterly", description = "Get quarterly revenue breakdown by product or service segment. If year and quarter are provided, returns only that quarter. If no filters provided, returns all available quarters.")
    public List<RevenueSegmentationReport> getRevenueSegmentationQuarterly(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023).") String year,
            @ToolParam(description = "Optional: specific quarter (e.g., Q1, Q2, Q3, Q4).") String quarter,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var data = financialStatementService.getRevenueSegmentation(ticker).block();
        if (data == null) return null;
        
        List<RevenueSegmentationReport> allReports = data.getQuarterlyReports();
        
        if (year == null || year.isBlank()) {
            if (quarter == null || quarter.isBlank()) {
                return allReports;
            }
            return allReports.stream()
                    .filter(r -> r.getPeriod() != null && r.getPeriod().contains(quarter))
                    .toList();
        }
        
        try {
            int yearInt = Integer.parseInt(year);
            
            if (quarter == null || quarter.isBlank()) {
                return allReports.stream()
                        .filter(r -> r.getFiscalYear() == yearInt)
                        .toList();
            }
            
            return allReports.stream()
                    .filter(r -> r.getFiscalYear() == yearInt)
                    .filter(r -> r.getPeriod() != null && r.getPeriod().contains(quarter))
                    .toList();
        } catch (NumberFormatException e) {
            return allReports;
        }
    }

    // ============== REVENUE GEOGRAPHIC ==============

    @Tool(name = "get_revenue_geographic_annual", description = "Get annual revenue breakdown by geographic region or country. If year is provided, returns only that year. If no year provided, returns all available years.")
    public List<RevenueGeographicSegmentationReport> getRevenueGeographicAnnual(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023). If not provided, returns all available years.") String year,
            ToolContext toolContext) {

        setTicker(ticker, toolContext);
        var data = financialStatementService.getRevenueGeographicSegmentation(ticker).block();
        if (data == null) return null;
        
        List<RevenueGeographicSegmentationReport> allReports = data.getReports();
        if (year == null || year.isBlank()) return allReports;
        
        try {
            int yearInt = Integer.parseInt(year);
            return allReports.stream()
                    .filter(r -> r.getFiscalYear() == yearInt)
                    .toList();
        } catch (NumberFormatException e) {
            return allReports;
        }
    }

    // ============== EARNINGS ==============

    @Tool(name = "get_earnings_history", description = "Get historical earnings data including actual EPS vs estimated EPS for past quarters")
    public com.testehan.finana.model.EarningsHistory getEarningsHistory(String ticker, ToolContext toolContext) {
        setTicker(ticker, toolContext);
        return earningsService.getEarningsHistory(ticker).block();
    }

    @Tool(name = "get_earnings_estimates", description = "Get analyst earnings estimates for upcoming quarters and years")
    public com.testehan.finana.model.EarningsEstimate getEarningsEstimates(String ticker, ToolContext toolContext) {
        setTicker(ticker, toolContext);
        return earningsService.getEarningsEstimates(ticker).block();
    }

    // ============== EARNINGS TRANSCRIPTS ==============

    @Tool(name = "get_earnings_transcript", description = "Get earnings call transcript for a specific quarter (format: YYYYQ, e.g., 2026Q1)")
    public QuarterlyEarningsTranscript getEarningsTranscript(
            String ticker,
            @ToolParam(description = "Fiscal quarter in format YYYYQ (e.g., 2026Q1, 2025Q4)") String quarter,
            ToolContext toolContext) {
        setTicker(ticker, toolContext);
        var data = companyEarningsTranscriptsRepository.findBySymbol(ticker).orElse(null);
        if (data == null || data.getTranscripts() == null) return null;
        
        List<QuarterlyEarningsTranscript> allTranscripts = data.getTranscripts();
        if (quarter == null || quarter.isBlank()) {
            return allTranscripts.isEmpty() ? null : allTranscripts.get(0);
        }
        
        return allTranscripts.stream()
                .filter(t -> t.getQuarter() != null && t.getQuarter().contains(quarter))
                .findFirst()
                .orElse(null);
    }

    @Tool(name = "get_earnings_transcripts_all", description = "Get all available earnings call transcripts for a ticker")
    public List<QuarterlyEarningsTranscript> getEarningsTranscriptsAll(String ticker, ToolContext toolContext) {
        setTicker(ticker, toolContext);
        var data = companyEarningsTranscriptsRepository.findBySymbol(ticker).orElse(null);
        return data != null ? data.getTranscripts() : null;
    }

    // ============== FINANCIAL ADJUSTMENTS ==============

    @Tool(name = "get_financial_adjustments_annual", description = "Get annual financial adjustments (restated numbers). Includes adjusted EPS (earnings per share), adjusted net income, adjusted EBITDA, adjusted operating income, adjusted free cash flow, and other adjusted financial metrics. If year is provided, returns only that year. If no year provided, returns all available years.")
    public List<FinancialAdjustmentReport> getFinancialAdjustmentsAnnual(
            String ticker,
            @ToolParam(description = "Optional: specific year (e.g., 2024, 2023). If not provided, returns all available years.") String year,
            ToolContext toolContext) {
        setTicker(ticker, toolContext);
        var data = financialAdjustmentRepository.findBySymbol(ticker).orElse(null);
        if (data == null || data.getAnnualAdjustments() == null) return null;
        
        List<FinancialAdjustmentReport> allReports = data.getAnnualAdjustments();
        if (year == null || year.isBlank()) return allReports;
        
        try {
            int yearInt = Integer.parseInt(year);
            return allReports.stream()
                    .filter(r -> r.getCalendarYear() == yearInt)
                    .toList();
        } catch (NumberFormatException e) {
            return allReports;
        }
    }

    // ============== SEC FILINGS ==============

    @Tool(name = "get_latest_sec_filing_annual", description = "Get the most recent SEC 10-K annual filing")
    public TenKFilings getLatestSecFilingAnnual(String ticker, ToolContext toolContext) {
        setTicker(ticker, toolContext);
        var data = secFilingRepository.findBySymbol(ticker).orElse(null);
        if (data == null || data.getTenKFilings() == null || data.getTenKFilings().isEmpty()) return null;
        return data.getTenKFilings().get(0);
    }

    @Tool(name = "get_latest_sec_filing_quarterly", description = "Get the most recent SEC 10-Q quarterly filing")
    public TenQFilings getLatestSecFilingQuarterly(String ticker, ToolContext toolContext) {
        setTicker(ticker, toolContext);
        var data = secFilingRepository.findBySymbol(ticker).orElse(null);
        if (data == null || data.getTenQFilings() == null || data.getTenQFilings().isEmpty()) return null;
        return data.getTenQFilings().get(0);
    }
}
