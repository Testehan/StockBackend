package com.testehan.finana.controller;

import com.testehan.finana.model.*;
import com.testehan.finana.service.AlphaVantageService;
import com.testehan.finana.service.FMPService;
import com.testehan.finana.service.FinancialDataOrchestrator;
import com.testehan.finana.service.FinancialDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/stocks")
public class StockController {

    private final AlphaVantageService alphaVantageService;
    private final FinancialDataService financialDataService;
    private final FMPService fmpService;

    private final FinancialDataOrchestrator financialDataOrchestrator;

    @Autowired
    public StockController(AlphaVantageService alphaVantageService, FinancialDataService financialDataService, FMPService fmpService, FinancialDataOrchestrator financialDataOrchestrator) {
        this.alphaVantageService = alphaVantageService;
        this.financialDataService = financialDataService;
        this.fmpService = fmpService;
        this.financialDataOrchestrator = financialDataOrchestrator;
    }

    @GetMapping("/overview/{symbol}")
    public Mono<CompanyOverview> getCompanyOverview(@PathVariable String symbol) {
        financialDataOrchestrator.ensureFinancialDataIsPresent(symbol.toUpperCase());
        return getCompanyOverviewFmp(symbol);
    }

    @GetMapping("/income-statement/{symbol}")
    public Mono<IncomeStatementData> getIncomeStatements(@PathVariable String symbol) {
        return financialDataService.getIncomeStatements(symbol);
    }

    @GetMapping("/balance-sheet/{symbol}")
    public Mono<BalanceSheetData> getBalanceSheetStatement(@PathVariable String symbol) {
        return financialDataService.getBalanceSheet(symbol);
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
        return Mono.just(financialDataService.getCompanyOverview(symbol).block().getFirst());
    }

    @GetMapping("/cash-flow/{symbol}")
    public Mono<CashFlowData> getCashFlow(@PathVariable String symbol) {
        return financialDataService.getCashFlow(symbol);
    }

    @GetMapping("/revenue-segmentation/{symbol}")
    public Mono<RevenueSegmentationData> getRevenueSegmentation(@PathVariable String symbol) {
        return financialDataService.getRevenueSegmentation(symbol);
    }

    @GetMapping("/earnings-history/{symbol}")
    public Mono<EarningsHistory> getEarningsHistory(@PathVariable String symbol) {
        return financialDataService.getEarningsHistory(symbol);
    }

    @GetMapping("/earnings-estimates/{symbol}")
    public Mono<EarningsEstimate> getEarningsEstimates(@PathVariable String symbol) {
        return financialDataService.getEarningsEstimates(symbol);
    }

    @GetMapping("/financial-ratios/{symbol}")
    public ResponseEntity<FinancialRatiosData> getFinancialRatios(@PathVariable String symbol) {
        Optional<FinancialRatiosData> financialRatiosData = financialDataService.getFinancialRatios(symbol);
        return financialRatiosData.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/global-quote/{symbol}")
    public Mono<GlobalQuote> getGlobalQuote(@PathVariable String symbol) {
        return financialDataService.getLastStockQuote(symbol.toUpperCase());
    }

    @GetMapping("/sp500-quote")
    public Mono<IndexQuotes> getSP500IndexQuote() {
        return financialDataService.getIndexQuotes("^GSPC");
    }

    @GetMapping("/earnings-call-transcript/{symbol}/{quarter}")
    public Mono<QuarterlyEarningsTranscript> getEarningsCallTranscript(@PathVariable String symbol, @PathVariable String quarter) {
        return financialDataService.getEarningsCallTranscript(symbol, quarter);
    }

    @DeleteMapping("/delete/{symbol}")
    public Mono<ResponseEntity<Void>> deleteStockData(@PathVariable String symbol) {
        return Mono.fromRunnable(() -> financialDataService.deleteFinancialData(symbol.toUpperCase()))
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
