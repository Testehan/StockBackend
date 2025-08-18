package com.testehan.finana.controller;

import com.testehan.finana.model.BalanceSheetData;
import com.testehan.finana.model.CashFlowData;
import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.IncomeStatementData;
import com.testehan.finana.model.SharesOutstandingData;
import com.testehan.finana.service.AlphaVantageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/stocks")
public class StockController {

    private final AlphaVantageService alphaVantageService;

    @Autowired
    public StockController(AlphaVantageService alphaVantageService) {
        this.alphaVantageService = alphaVantageService;
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
}
