package com.testehan.finana.controller;

import com.testehan.finana.model.*;
import com.testehan.finana.service.AlphaVantageService;
import com.testehan.finana.service.FinancialDataService;
import com.testehan.finana.service.FinancialMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping("/stocks")
public class StockController {

    private final AlphaVantageService alphaVantageService;
    private final FinancialDataService financialDataService;
    private final FinancialMetricsService financialMetricsService;

    @Autowired
    public StockController(AlphaVantageService alphaVantageService, FinancialDataService financialDataService, FinancialMetricsService financialMetricsService) {
        this.alphaVantageService = alphaVantageService;
        this.financialDataService = financialDataService;
        this.financialMetricsService = financialMetricsService;
    }

    @GetMapping("/overview/{symbol}")
    public Mono<CompanyOverview> getCompanyOverview(@PathVariable String symbol) {
        return financialDataService.getCompanyOverview(symbol);
    }

    @GetMapping("/income-statement/{symbol}")
    public Mono<IncomeStatementData> getIncomeStatements(@PathVariable String symbol) {
        return financialDataService.getIncomeStatements(symbol);
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

    @GetMapping("/financial-ratios/{symbol}")
    public ResponseEntity<FinancialRatiosData> getFinancialRatios(@PathVariable String symbol) {
        Optional<FinancialRatiosData> financialRatiosData = financialMetricsService.getFinancialRatios(symbol);
        return financialRatiosData.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
