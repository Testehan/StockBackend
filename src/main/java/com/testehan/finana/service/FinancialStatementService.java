package com.testehan.finana.service;

import com.testehan.finana.model.finstatement.BalanceSheetData;
import com.testehan.finana.model.finstatement.CashFlowData;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.model.finstatement.RevenueGeographicSegmentationData;
import com.testehan.finana.model.finstatement.RevenueSegmentationData;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.CashFlowRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.repository.RevenueGeographicSegmentationRepository;
import com.testehan.finana.repository.RevenueSegmentationDataRepository;
import com.testehan.finana.util.data.FmpDataCleaner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class FinancialStatementService {
    private final FMPService fmpService;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    private final RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository;

    public FinancialStatementService(FMPService fmpService, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository, CashFlowRepository cashFlowRepository, RevenueSegmentationDataRepository revenueSegmentationDataRepository, RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository) {
        this.fmpService = fmpService;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.revenueSegmentationDataRepository = revenueSegmentationDataRepository;
        this.revenueGeographicSegmentationRepository = revenueGeographicSegmentationRepository;
    }

    public Mono<IncomeStatementData> getIncomeStatements(String symbol) {
        return Mono.fromCallable(() -> incomeStatementRepository.findBySymbol(symbol.toUpperCase()))
                .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                .switchIfEmpty(Mono.defer(() -> 
                    Mono.zip(
                            fmpService.getIncomeStatement(symbol.toUpperCase(), "annual"),
                            fmpService.getIncomeStatement(symbol.toUpperCase(), "quarter")
                    ).flatMap(tuple -> {
                        IncomeStatementData incomeStatementData = new IncomeStatementData();
                        incomeStatementData.setSymbol(symbol);
                        incomeStatementData.setAnnualReports(FmpDataCleaner.cleanIncomeStatements(tuple.getT1()));
                        incomeStatementData.setQuarterlyReports(FmpDataCleaner.cleanIncomeStatements(tuple.getT2()));
                        return Mono.fromCallable(() -> incomeStatementRepository.save(incomeStatementData));
                    })
                ))
                .onErrorResume(DuplicateKeyException.class, e -> 
                    Mono.fromCallable(() -> incomeStatementRepository.findBySymbol(symbol.toUpperCase()))
                            .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                );
    }

    public Mono<BalanceSheetData> getBalanceSheet(String symbol) {
        return Mono.fromCallable(() -> balanceSheetRepository.findBySymbol(symbol.toUpperCase()))
                .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                .switchIfEmpty(Mono.defer(() -> 
                    Mono.zip(
                            fmpService.getBalanceSheetStatement(symbol.toUpperCase(), "annual"),
                            fmpService.getBalanceSheetStatement(symbol.toUpperCase(), "quarter")
                    ).flatMap(tuple -> {
                        BalanceSheetData balanceSheetData = new BalanceSheetData();
                        balanceSheetData.setSymbol(symbol);
                        balanceSheetData.setAnnualReports(FmpDataCleaner.cleanBalanceSheets(tuple.getT1()));
                        balanceSheetData.setQuarterlyReports(FmpDataCleaner.cleanBalanceSheets(tuple.getT2()));
                        return Mono.fromCallable(() -> balanceSheetRepository.save(balanceSheetData));
                    })
                ))
                .onErrorResume(DuplicateKeyException.class, e -> 
                    Mono.fromCallable(() -> balanceSheetRepository.findBySymbol(symbol.toUpperCase()))
                            .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                );
    }

    public Mono<CashFlowData> getCashFlow(String symbol) {
        return Mono.fromCallable(() -> cashFlowRepository.findBySymbol(symbol.toUpperCase()))
                .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                .switchIfEmpty(Mono.defer(() -> 
                    Mono.zip(
                            fmpService.getCashflowStatement(symbol.toUpperCase(), "annual"),
                            fmpService.getCashflowStatement(symbol.toUpperCase(), "quarter")
                    ).flatMap(tuple -> {
                        CashFlowData cashFlowData = new CashFlowData();
                        cashFlowData.setSymbol(symbol);
                        cashFlowData.setAnnualReports(FmpDataCleaner.cleanCashFlows(tuple.getT1()));
                        cashFlowData.setQuarterlyReports(FmpDataCleaner.cleanCashFlows(tuple.getT2()));
                        return Mono.fromCallable(() -> cashFlowRepository.save(cashFlowData));
                    })
                ))
                .onErrorResume(DuplicateKeyException.class, e -> 
                    Mono.fromCallable(() -> cashFlowRepository.findBySymbol(symbol.toUpperCase()))
                            .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                );
    }

    public Mono<RevenueSegmentationData> getRevenueSegmentation(String symbol) {
        return Mono.fromCallable(() -> revenueSegmentationDataRepository.findBySymbol(symbol.toUpperCase()))
                .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                .switchIfEmpty(Mono.defer(() -> 
                    fmpService.getRevenueSegmentation(symbol.toUpperCase(), "annual")
                            .flatMap(annualReports -> {
                                RevenueSegmentationData revenueSegmentationData = new RevenueSegmentationData();
                                revenueSegmentationData.setSymbol(symbol);
                                revenueSegmentationData.setAnnualReports(annualReports);
                                return Mono.fromCallable(() -> revenueSegmentationDataRepository.save(revenueSegmentationData));
                            })
                ))
                .onErrorResume(DuplicateKeyException.class, e -> 
                    Mono.fromCallable(() -> revenueSegmentationDataRepository.findBySymbol(symbol.toUpperCase()))
                            .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                );
    }

    public Mono<RevenueGeographicSegmentationData> getRevenueGeographicSegmentation(String symbol) {
        return Mono.fromCallable(() -> revenueGeographicSegmentationRepository.findBySymbol(symbol.toUpperCase()))
                .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                .switchIfEmpty(Mono.defer(() -> 
                    fmpService.getRevenueGeographicSegmentation(symbol.toUpperCase(), "annual")
                            .flatMap(reports -> {
                                RevenueGeographicSegmentationData data = new RevenueGeographicSegmentationData();
                                data.setSymbol(symbol);
                                data.setReports(reports);
                                return Mono.fromCallable(() -> revenueGeographicSegmentationRepository.save(data));
                            })
                ))
                .onErrorResume(DuplicateKeyException.class, e -> 
                    Mono.fromCallable(() -> revenueGeographicSegmentationRepository.findBySymbol(symbol.toUpperCase()))
                            .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                );
    }

    public void deleteIncomeStatementsBySymbol(String symbol) {
        incomeStatementRepository.deleteBySymbol(symbol);
    }

    public void deleteBalanceSheetBySymbol(String symbol) {
        balanceSheetRepository.deleteBySymbol(symbol);
    }

    public void deleteCashFlowBySymbol(String symbol) {
        cashFlowRepository.deleteBySymbol(symbol);
    }

    public void deleteRevenueSegmentationBySymbol(String symbol) {
        revenueSegmentationDataRepository.deleteBySymbol(symbol);
    }

    public void deleteRevenueGeographicSegmentationBySymbol(String symbol) {
        revenueGeographicSegmentationRepository.deleteBySymbol(symbol);
    }

    public boolean hasIncomeStatements(String symbol) {
        return incomeStatementRepository.findBySymbol(symbol).isPresent();
    }

    public boolean hasBalanceSheet(String symbol) {
        return balanceSheetRepository.findBySymbol(symbol).isPresent();
    }

    public boolean hasCashFlow(String symbol) {
        return cashFlowRepository.findBySymbol(symbol).isPresent();
    }

    public boolean hasRevenueSegmentation(String symbol) {
        return revenueSegmentationDataRepository.findBySymbol(symbol).isPresent();
    }

    public boolean hasRevenueGeographicSegmentation(String symbol) {
        return revenueGeographicSegmentationRepository.findBySymbol(symbol).isPresent();
    }
}