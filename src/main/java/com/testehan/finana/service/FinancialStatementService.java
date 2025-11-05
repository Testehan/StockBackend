package com.testehan.finana.service;

import com.testehan.finana.model.BalanceSheetData;
import com.testehan.finana.model.CashFlowData;
import com.testehan.finana.model.IncomeStatementData;
import com.testehan.finana.model.RevenueGeographicSegmentationData;
import com.testehan.finana.model.RevenueSegmentationData;
import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.CashFlowRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.repository.RevenueGeographicSegmentationRepository;
import com.testehan.finana.repository.RevenueSegmentationDataRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;

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
        return Mono.justOrEmpty(incomeStatementRepository.findBySymbol(symbol.toUpperCase()))
                .switchIfEmpty(
                        Mono.zip(
                                fmpService.getIncomeStatement(symbol.toUpperCase(), "annual"),
                                fmpService.getIncomeStatement(symbol.toUpperCase(), "quarter")
                        ).flatMap(tuple -> {
                            IncomeStatementData incomeStatementData = new IncomeStatementData();
                            incomeStatementData.setSymbol(symbol);
                            incomeStatementData.setAnnualReports(tuple.getT1());
                            incomeStatementData.setQuarterlyReports(tuple.getT2());
                            return Mono.just(incomeStatementRepository.save(incomeStatementData));
                        })
                );
    }

    public Mono<BalanceSheetData> getBalanceSheet(String symbol) {
        return Mono.justOrEmpty(balanceSheetRepository.findBySymbol(symbol.toUpperCase()))
                .switchIfEmpty(
                        Mono.zip(
                                fmpService.getBalanceSheetStatement(symbol.toUpperCase(), "annual"),
                                fmpService.getBalanceSheetStatement(symbol.toUpperCase(), "quarter")
                        ).flatMap(tuple -> {
                            BalanceSheetData balanceSheetData = new BalanceSheetData();
                            balanceSheetData.setSymbol(symbol);
                            balanceSheetData.setAnnualReports(tuple.getT1());
                            balanceSheetData.setQuarterlyReports(tuple.getT2());
                            return Mono.just(balanceSheetRepository.save(balanceSheetData));
                        })
                );
    }

    public Mono<CashFlowData> getCashFlow(String symbol) {
        return Mono.justOrEmpty(cashFlowRepository.findBySymbol(symbol.toUpperCase()))
                .switchIfEmpty(
                        Mono.zip(
                                fmpService.getCashflowStatement(symbol.toUpperCase(), "annual"),
                                fmpService.getCashflowStatement(symbol.toUpperCase(), "quarter")
                        ).flatMap(tuple -> {
                            CashFlowData cashFlowData = new CashFlowData();
                            cashFlowData.setSymbol(symbol);
                            cashFlowData.setAnnualReports(tuple.getT1());
                            cashFlowData.setQuarterlyReports(tuple.getT2());
                            return Mono.just(cashFlowRepository.save(cashFlowData));
                        })
                );
    }

    public Mono<RevenueSegmentationData> getRevenueSegmentation(String symbol) {
        return Mono.justOrEmpty(revenueSegmentationDataRepository.findBySymbol(symbol.toUpperCase()))
                .switchIfEmpty(
                        fmpService.getRevenueSegmentation(symbol.toUpperCase(), "annual")
                                .flatMap(annualReports -> {
                                    RevenueSegmentationData revenueSegmentationData = new RevenueSegmentationData();
                                    revenueSegmentationData.setSymbol(symbol);
                                    revenueSegmentationData.setAnnualReports(annualReports);
                                    return Mono.just(revenueSegmentationDataRepository.save(revenueSegmentationData));
                                })
                );
    }

    public Mono<RevenueGeographicSegmentationData> getRevenueGeographicSegmentation(String symbol) {
        return Mono.justOrEmpty(revenueGeographicSegmentationRepository.findBySymbol(symbol.toUpperCase()))
                .switchIfEmpty(
                        fmpService.getRevenueGeographicSegmentation(symbol.toUpperCase(), "annual")
                                .flatMap(reports -> {
                                    RevenueGeographicSegmentationData data = new RevenueGeographicSegmentationData();
                                    data.setSymbol(symbol);
                                    data.setReports(reports);
                                    return Mono.just(revenueGeographicSegmentationRepository.save(data));
                                })
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