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
        return Mono.defer(() -> {
            Optional<IncomeStatementData> incomeStatementsFromDb = incomeStatementRepository.findBySymbol(symbol.toUpperCase());
            if (incomeStatementsFromDb.isPresent()) {
                return Mono.just(incomeStatementsFromDb.get());
            } else {
                IncomeStatementData incomeStatementData = new IncomeStatementData();
                incomeStatementData.setSymbol(symbol);
                incomeStatementData.setAnnualReports(fmpService.getIncomeStatement(symbol.toUpperCase(),"annual").block());
                incomeStatementData.setQuarterlyReports(fmpService.getIncomeStatement(symbol.toUpperCase(),"quarter").block());

                return Mono.just(incomeStatementRepository.save(incomeStatementData));
            }
        });
    }

    public Mono<BalanceSheetData> getBalanceSheet(String symbol) {
        return Mono.defer(() -> {
            Optional<BalanceSheetData> balanceSheetFromDb = balanceSheetRepository.findBySymbol(symbol.toUpperCase());
            if (balanceSheetFromDb.isPresent()) {
                return Mono.just(balanceSheetFromDb.get());
            } else {
                BalanceSheetData balanceSheetData = new BalanceSheetData();
                balanceSheetData.setSymbol(symbol);
                balanceSheetData.setAnnualReports(fmpService.getBalanceSheetStatement(symbol.toUpperCase(),"annual").block());
                balanceSheetData.setQuarterlyReports(fmpService.getBalanceSheetStatement(symbol.toUpperCase(),"quarter").block());

                return Mono.just(balanceSheetRepository.save(balanceSheetData));
            }
        });
    }

    public Mono<CashFlowData> getCashFlow(String symbol) {
        return Mono.defer(() -> {
            Optional<CashFlowData> cashFlowFromDb = cashFlowRepository.findBySymbol(symbol.toUpperCase());
            if (cashFlowFromDb.isPresent()) {
                return Mono.just(cashFlowFromDb.get());
            } else {
                CashFlowData cashFlowData = new CashFlowData();
                cashFlowData.setSymbol(symbol);
                cashFlowData.setAnnualReports(fmpService.getCashflowStatement(symbol.toUpperCase(),"annual").block());
                cashFlowData.setQuarterlyReports(fmpService.getCashflowStatement(symbol.toUpperCase(),"quarter").block());

                return Mono.just(cashFlowRepository.save(cashFlowData));
            }
        });
    }

    public Mono<RevenueSegmentationData> getRevenueSegmentation(String symbol) {
        return Mono.defer(() -> {
            Optional<RevenueSegmentationData> revenueSegmentationFromDb = revenueSegmentationDataRepository.findBySymbol(symbol.toUpperCase());
            if (revenueSegmentationFromDb.isPresent()) {
                return Mono.just(revenueSegmentationFromDb.get());
            } else {
                RevenueSegmentationData revenueSegmentationData = new RevenueSegmentationData();
                revenueSegmentationData.setSymbol(symbol);
                revenueSegmentationData.setAnnualReports(fmpService.getRevenueSegmentation(symbol.toUpperCase(),"annual").block());
                // below is part of the paid plan annual subscription for this API...yearly info is good enough
//                revenueSegmentationData.setQuarterlyReports(fmpService.getRevenueSegmentation(symbol.toUpperCase(),"quarter").block());

                return Mono.just(revenueSegmentationDataRepository.save(revenueSegmentationData));
            }
        });
    }

    public Mono<RevenueGeographicSegmentationData> getRevenueGeographicSegmentation(String symbol) {
        return Mono.defer(() -> {
            Optional<RevenueGeographicSegmentationData> revenueGeographicSegmentationFromDb = revenueGeographicSegmentationRepository.findBySymbol(symbol.toUpperCase());
            if (revenueGeographicSegmentationFromDb.isPresent()) {
                return Mono.just(revenueGeographicSegmentationFromDb.get());
            } else {
                RevenueGeographicSegmentationData revenueGeographicSegmentationData = new RevenueGeographicSegmentationData();
                revenueGeographicSegmentationData.setSymbol(symbol);
                revenueGeographicSegmentationData.setReports(fmpService.getRevenueGeographicSegmentation(symbol.toUpperCase(),"annual").block());

                return Mono.just(revenueGeographicSegmentationRepository.save(revenueGeographicSegmentationData));
            }
        });
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
