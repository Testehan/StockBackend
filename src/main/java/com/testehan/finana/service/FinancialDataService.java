package com.testehan.finana.service;

import com.testehan.finana.model.*;
import com.testehan.finana.repository.*;
import com.testehan.finana.util.FinancialRatiosCalculator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class FinancialDataService {

    private final AlphaVantageService alphaVantageService;

    private final CompanyOverviewRepository companyOverviewRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final SharesOutstandingRepository sharesOutstandingRepository;

    public FinancialDataService(AlphaVantageService alphaVantageService, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository, CashFlowRepository cashFlowRepository, SharesOutstandingRepository sharesOutstandingRepository, FinancialRatiosRepository financialRatiosRepository, FinancialRatiosCalculator financialRatiosCalculator, CompanyOverviewRepository companyOverviewRepository) {
        this.alphaVantageService = alphaVantageService;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.sharesOutstandingRepository = sharesOutstandingRepository;
        this.companyOverviewRepository = companyOverviewRepository;
    }

    public Mono<CompanyOverview> getCompanyOverview(String symbol) {
        return Mono.defer(() -> {
            Optional<CompanyOverview> overviewFromDb = companyOverviewRepository.findBySymbol(symbol.toUpperCase());
            if (overviewFromDb.isPresent() && isRecent(overviewFromDb.get().getLastUpdated())) {
                return Mono.just(overviewFromDb.get());
            } else {
                return Mono.just(companyOverviewRepository.save(alphaVantageService.fetchCompanyOverviewFromApiAndSave(symbol.toUpperCase(), overviewFromDb).block()));
            }
        });
    }

    public Mono<IncomeStatementData> getIncomeStatements(String symbol) {
        return Mono.defer(() -> {
            Optional<IncomeStatementData> incomeStatementsFromDb = incomeStatementRepository.findBySymbol(symbol.toUpperCase());
            if (incomeStatementsFromDb.isPresent()) {
                return Mono.just(incomeStatementsFromDb.get());
            } else {
                return  Mono.just(incomeStatementRepository.save(alphaVantageService.fetchIncomeStatementsFromApiAndSave(symbol.toUpperCase()).block()));
            }
        });
    }

    public Mono<BalanceSheetData> getBalanceSheet(String symbol) {
        return Mono.defer(() -> {
            Optional<BalanceSheetData> balanceSheetFromDb = balanceSheetRepository.findBySymbol(symbol.toUpperCase());
            if (balanceSheetFromDb.isPresent()) {
                return Mono.just(balanceSheetFromDb.get());
            } else {
                return Mono.just(balanceSheetRepository.save(alphaVantageService.fetchBalanceSheetFromApiAndSave(symbol.toUpperCase()).block()));
            }
        });
    }

    public Mono<CashFlowData> getCashFlow(String symbol) {
        return Mono.defer(() -> {
            Optional<CashFlowData> cashFlowFromDb = cashFlowRepository.findBySymbol(symbol.toUpperCase());
            if (cashFlowFromDb.isPresent()) {
                return Mono.just(cashFlowFromDb.get());
            } else {
                return Mono.just(cashFlowRepository.save(alphaVantageService.fetchCashFlowFromApiAndSave(symbol.toUpperCase()).block()));
            }
        });
    }

    public Mono<SharesOutstandingData> getSharesOutstanding(String symbol) {
        return Mono.defer(() -> {
            Optional<SharesOutstandingData> sharesOutstandingFromDb = sharesOutstandingRepository.findBySymbol(symbol.toUpperCase());
            if (sharesOutstandingFromDb.isPresent()) {
                return Mono.just(sharesOutstandingFromDb.get());
            } else {
                return Mono.just(sharesOutstandingRepository.save(alphaVantageService.fetchSharesOutstandingFromApiAndSave(symbol.toUpperCase()).block()));
            }
        });
    }

    private boolean isRecent(LocalDateTime lastUpdated) {
        if (lastUpdated == null) {
            return false;
        }
        return ChronoUnit.WEEKS.between(lastUpdated, LocalDateTime.now()) < 1;
    }
}
