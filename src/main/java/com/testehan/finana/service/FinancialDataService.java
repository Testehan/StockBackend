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
    private final EarningsHistoryRepository earningsHistoryRepository;

    public FinancialDataService(AlphaVantageService alphaVantageService, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository, CashFlowRepository cashFlowRepository, SharesOutstandingRepository sharesOutstandingRepository, FinancialRatiosRepository financialRatiosRepository, FinancialRatiosCalculator financialRatiosCalculator, CompanyOverviewRepository companyOverviewRepository, EarningsHistoryRepository earningsHistoryRepository) {
        this.alphaVantageService = alphaVantageService;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.sharesOutstandingRepository = sharesOutstandingRepository;
        this.companyOverviewRepository = companyOverviewRepository;
        this.earningsHistoryRepository = earningsHistoryRepository;
    }

    public Mono<EarningsHistory> getEarningsHistory(String symbol) {
        return Mono.defer(() -> {
            Optional<EarningsHistory> earningsHistoryFromDb = earningsHistoryRepository.findBySymbol(symbol.toUpperCase());
            if (earningsHistoryFromDb.isPresent()) {
                return Mono.just(earningsHistoryFromDb.get());
            } else {
                return alphaVantageService.fetchEarningsHistoryFromApiAndSave(symbol.toUpperCase())
                        .flatMap(earningsHistory -> Mono.just(earningsHistoryRepository.save(earningsHistory)));
            }
        });
    }

    public Mono<CompanyOverview> getCompanyOverview(String symbol) {
        return Mono.defer(() -> {
            Optional<CompanyOverview> overviewFromDb = companyOverviewRepository.findBySymbol(symbol.toUpperCase());
            if (overviewFromDb.isPresent() && isRecent(overviewFromDb.get().getLastUpdated())) {
                return Mono.just(overviewFromDb.get());
            } else {
                return alphaVantageService.fetchCompanyOverviewFromApiAndSave(symbol.toUpperCase(), overviewFromDb)
                        .flatMap(overview -> Mono.just(companyOverviewRepository.save(overview)));
            }
        });
    }

    public Mono<IncomeStatementData> getIncomeStatements(String symbol) {
        return Mono.defer(() -> {
            Optional<IncomeStatementData> incomeStatementsFromDb = incomeStatementRepository.findBySymbol(symbol.toUpperCase());
            if (incomeStatementsFromDb.isPresent()) {
                return Mono.just(incomeStatementsFromDb.get());
            } else {
                return alphaVantageService.fetchIncomeStatementsFromApiAndSave(symbol.toUpperCase())
                        .flatMap(incomeStatementData -> Mono.just(incomeStatementRepository.save(incomeStatementData)));
            }
        });
    }

    public Mono<BalanceSheetData> getBalanceSheet(String symbol) {
        return Mono.defer(() -> {
            Optional<BalanceSheetData> balanceSheetFromDb = balanceSheetRepository.findBySymbol(symbol.toUpperCase());
            if (balanceSheetFromDb.isPresent()) {
                return Mono.just(balanceSheetFromDb.get());
            } else {
                return alphaVantageService.fetchBalanceSheetFromApiAndSave(symbol.toUpperCase())
                        .flatMap(balanceSheetData -> Mono.just(balanceSheetRepository.save(balanceSheetData)));
            }
        });
    }

    public Mono<CashFlowData> getCashFlow(String symbol) {
        return Mono.defer(() -> {
            Optional<CashFlowData> cashFlowFromDb = cashFlowRepository.findBySymbol(symbol.toUpperCase());
            if (cashFlowFromDb.isPresent()) {
                return Mono.just(cashFlowFromDb.get());
            } else {
                return alphaVantageService.fetchCashFlowFromApiAndSave(symbol.toUpperCase())
                        .flatMap(cashFlowData -> Mono.just(cashFlowRepository.save(cashFlowData)));
            }
        });
    }

    public Mono<SharesOutstandingData> getSharesOutstanding(String symbol) {
        return Mono.defer(() -> {
            Optional<SharesOutstandingData> sharesOutstandingFromDb = sharesOutstandingRepository.findBySymbol(symbol.toUpperCase());
            if (sharesOutstandingFromDb.isPresent()) {
                return Mono.just(sharesOutstandingFromDb.get());
            } else {
                return alphaVantageService.fetchSharesOutstandingFromApiAndSave(symbol.toUpperCase())
                        .flatMap(sharesOutstandingData -> Mono.just(sharesOutstandingRepository.save(sharesOutstandingData)));
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
