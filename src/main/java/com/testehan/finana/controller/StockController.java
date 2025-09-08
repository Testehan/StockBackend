package com.testehan.finana.controller;

import com.testehan.finana.model.*;
import com.testehan.finana.service.AlphaVantageService;
import com.testehan.finana.service.FMPService;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.FinancialMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/stocks")
public class StockController {

    private final AlphaVantageService alphaVantageService;
    private final FinancialDataService financialDataService;
    private final FinancialMetricsService financialMetricsService;
    private final FMPService fmpService;

    @Autowired
    public StockController(AlphaVantageService alphaVantageService, FinancialDataService financialDataService, FinancialMetricsService financialMetricsService, FMPService fmpService) {
        this.alphaVantageService = alphaVantageService;
        this.financialDataService = financialDataService;
        this.financialMetricsService = financialMetricsService;
        this.fmpService = fmpService;
    }

    @GetMapping("/overview/{symbol}")
    public Mono<CompanyOverview> getCompanyOverview(@PathVariable String symbol) {
        return financialDataService.getCompanyOverview(symbol);
    }

    @GetMapping("/income-statement/{symbol}")
    public Mono<IncomeStatementData> getIncomeStatements(@PathVariable String symbol) {
        return financialDataService.getIncomeStatements(symbol);
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

    @GetMapping("/balance-sheet/{symbol}")
    public Mono<BalanceSheetData> getBalanceSheet(@PathVariable String symbol) {
        return financialDataService.getBalanceSheet(symbol);
    }

    @GetMapping("/cash-flow/{symbol}")
    public Mono<CashFlowData> getCashFlow(@PathVariable String symbol) {
        return financialDataService.getCashFlow(symbol);
    }

    @GetMapping("/shares-outstanding/{symbol}")
    public Mono<SharesOutstandingData> getSharesOutstanding(@PathVariable String symbol) {
        return financialDataService.getSharesOutstanding(symbol);
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
        Optional<FinancialRatiosData> financialRatiosData = financialMetricsService.getFinancialRatios(symbol);
        return financialRatiosData.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/global-quote/{symbol}")
    public Mono<GlobalQuote> getGlobalQuote(@PathVariable String symbol) {
        return financialDataService.getGlobalQuote(symbol);
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
