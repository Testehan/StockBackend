package com.testehan.finana.controller;

import com.testehan.finana.model.adjustment.FinancialAdjustment;
import com.testehan.finana.model.*;
import com.testehan.finana.model.filing.QuarterlyEarningsTranscript;
import com.testehan.finana.model.finstatement.*;
import com.testehan.finana.model.quote.GlobalQuote;
import com.testehan.finana.model.quote.IndexQuotes;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/stocks")
public class StockController {

    private final AlphaVantageService alphaVantageService;
    private final FMPService fmpService;
    private final FinancialDataOrchestrator financialDataOrchestrator;
    private final CompanyDataService companyDataService;
    private final FinancialStatementService financialStatementService;
    private final EarningsService earningsService;
    private final QuoteService quoteService;
    private final FinancialDataService financialDataService; // For remaining methods (ratios)
    private final AdjustmentService adjustmentService;

    public StockController(AlphaVantageService alphaVantageService, FMPService fmpService, FinancialDataOrchestrator financialDataOrchestrator, CompanyDataService companyDataService, FinancialStatementService financialStatementService, EarningsService earningsService, QuoteService quoteService, FinancialDataService financialDataService, AdjustmentService adjustmentService) {
        this.alphaVantageService = alphaVantageService;
        this.fmpService = fmpService;
        this.financialDataOrchestrator = financialDataOrchestrator;
        this.companyDataService = companyDataService;
        this.financialStatementService = financialStatementService;
        this.earningsService = earningsService;
        this.quoteService = quoteService;
        this.financialDataService = financialDataService; // For remaining methods (ratios)
        this.adjustmentService = adjustmentService;
    }

    @GetMapping("/adjustments/{symbol}")
    public ResponseEntity<FinancialAdjustment> getFinancialAdjustments(@PathVariable String symbol) {
        return ResponseEntity.ok(adjustmentService.getFinancialAdjustments(symbol.toUpperCase()));
    }

    @GetMapping("/overview/{symbol}")
    public Mono<CompanyOverview> getCompanyOverview(@PathVariable String symbol) {
//        return financialDataOrchestrator.ensureFinancialDataIsPresent(symbol.toUpperCase())
//                .then(companyDataService.getCompanyOverview(symbol).map(list -> list.getFirst()));

        return companyDataService.getCompanyOverview(symbol).map(list -> list.getFirst());
    }

    @GetMapping("/presentfinancialdata/{symbol}")
    public FinancialDataAvailability getFinancialDataAvailability(@PathVariable String symbol) {
        return financialDataOrchestrator.checkFinancialDataAvailability(symbol);
    }



    @GetMapping("/income-statement/{symbol}")
    public Mono<IncomeStatementData> getIncomeStatements(@PathVariable String symbol) {
        return financialStatementService.getIncomeStatements(symbol);
    }

    @GetMapping("/balance-sheet/{symbol}")
    public Mono<BalanceSheetData> getBalanceSheetStatement(@PathVariable String symbol) {
        return financialStatementService.getBalanceSheet(symbol);
    }

    @GetMapping("/fmp/income-statement/{symbol}/{period}")
    public Mono<List<IncomeReport>> getIncomeStatement(@PathVariable String symbol, @PathVariable String period) {
        return fmpService.getIncomeStatement(symbol,period);
    }

    @GetMapping("/fmp/balance-sheet-statement/{symbol}/{period}")
    public Mono<List<BalanceSheetReport>> getBalanceSheetStatement(@PathVariable String symbol, @PathVariable String period) {
        return fmpService.getBalanceSheetStatement(symbol, period);
    }

    @GetMapping("/fmp/cash-flow-statement/{symbol}/{period}")
    public Mono<List<CashFlowReport>> getCashFlowStatement(@PathVariable String symbol, @PathVariable String period) {
        return fmpService.getCashflowStatement(symbol, period);
    }

    @GetMapping("/fmp/company-overview/{symbol}")
    public Mono<CompanyOverview> getCompanyOverviewFmp(@PathVariable String symbol) {
        return companyDataService.getCompanyOverview(symbol).map(list -> list.getFirst());
    }

    @GetMapping("/cash-flow/{symbol}")
    public Mono<CashFlowData> getCashFlow(@PathVariable String symbol) {
        return financialStatementService.getCashFlow(symbol);
    }

    @GetMapping("/revenue-segmentation/{symbol}")
    public Mono<RevenueSegmentationData> getRevenueSegmentation(@PathVariable String symbol) {
        return financialStatementService.getRevenueSegmentation(symbol);
    }

    @GetMapping("/revenue-geography/{symbol}")
    public Mono<RevenueGeographicSegmentationData> getRevenueGeography(@PathVariable String symbol) {
        return financialStatementService.getRevenueGeographicSegmentation(symbol);
    }

    @GetMapping("/earnings-history/{symbol}")
    public Mono<EarningsHistory> getEarningsHistory(@PathVariable String symbol) {
        return earningsService.getEarningsHistory(symbol);
    }

    @GetMapping("/earnings-estimates/{symbol}")
    public Mono<EarningsEstimate> getEarningsEstimates(@PathVariable String symbol) {
        return earningsService.getEarningsEstimates(symbol);
    }

    @GetMapping("/financial-ratios/{symbol}")
    public Mono<ResponseEntity<FinancialRatiosData>> getFinancialRatios(@PathVariable String symbol) {
        return financialDataService.getFinancialRatios(symbol)
                .map(opt -> opt.map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build()));
    }

    @GetMapping("/global-quote/{symbol}")
    public Mono<GlobalQuote> getGlobalQuote(@PathVariable String symbol) {
        return quoteService.getLastStockQuote(symbol.toUpperCase());
    }

    @GetMapping("/sp500-quote")
    public Mono<IndexQuotes> getSP500IndexQuote() {
        return quoteService.getIndexQuotes("^GSPC");
    }

    @GetMapping("/earnings-call-transcript/{symbol}/{quarter}")
    public Mono<QuarterlyEarningsTranscript> getEarningsCallTranscript(@PathVariable String symbol, @PathVariable String quarter) {
        return earningsService.getEarningsCallTranscript(symbol, quarter);
    }

    @DeleteMapping("/delete/{symbol}")
    public Mono<ResponseEntity<Void>> deleteStockData(@PathVariable String symbol) {
        return Mono.fromRunnable(() -> financialDataOrchestrator.deleteFinancialData(symbol.toUpperCase()))
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
