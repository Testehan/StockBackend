package com.testehan.finana.controller;

import com.testehan.finana.model.*;
import com.testehan.finana.service.AlphaVantageService;
import com.testehan.finana.service.FinancialMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/stocks")
public class StockController {

    private final AlphaVantageService alphaVantageService;
    private final FinancialMetricsService financialMetricsService;

    @Autowired
    public StockController(AlphaVantageService alphaVantageService, FinancialMetricsService financialMetricsService) {
        this.alphaVantageService = alphaVantageService;
        this.financialMetricsService = financialMetricsService;
    }

    @GetMapping("/overview/{symbol}")
    public Mono<CompanyOverview> getCompanyOverview(@PathVariable String symbol) {
        return alphaVantageService.getCompanyOverview(symbol);
    }

    @GetMapping("/income-statement/{symbol}")
    public Mono<IncomeStatementData> getIncomeStatements(@PathVariable String symbol) {
        return alphaVantageService.getIncomeStatements(symbol);
    }

    @GetMapping("/balance-sheet/{symbol}")
    public Mono<BalanceSheetData> getBalanceSheet(@PathVariable String symbol) {
        return alphaVantageService.getBalanceSheet(symbol);
    }

    @GetMapping("/cash-flow/{symbol}")
    public Mono<CashFlowData> getCashFlow(@PathVariable String symbol) {
        return alphaVantageService.getCashFlow(symbol);
    }

    @GetMapping("/shares-outstanding/{symbol}")
    public Mono<SharesOutstandingData> getSharesOutstanding(@PathVariable String symbol) {
        return alphaVantageService.getSharesOutstanding(symbol);
    }

    @GetMapping("/financial-ratios/{symbol}")
    public List<FinancialRatios> getFinancialRatios(@PathVariable String symbol) {
        return financialMetricsService.getFinancialRatios(symbol);
    }
}
