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
import com.testehan.finana.util.DateUtils;
import com.testehan.finana.util.data.FmpDataCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class FinancialStatementService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialStatementService.class);
    
    private final FMPService fmpService;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    private final RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository;
    private final DateUtils dateUtils;

    public FinancialStatementService(FMPService fmpService, IncomeStatementRepository incomeStatementRepository, BalanceSheetRepository balanceSheetRepository, CashFlowRepository cashFlowRepository, RevenueSegmentationDataRepository revenueSegmentationDataRepository, RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository, DateUtils dateUtils) {
        this.fmpService = fmpService;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.revenueSegmentationDataRepository = revenueSegmentationDataRepository;
        this.revenueGeographicSegmentationRepository = revenueGeographicSegmentationRepository;
        this.dateUtils = dateUtils;
    }

    public Mono<IncomeStatementData> getIncomeStatements(String symbol) {
        return Mono.defer(() -> {
            var existing = incomeStatementRepository.findBySymbol(symbol.toUpperCase());
            if (existing.isPresent() && dateUtils.isRecent(existing.get().getLastUpdated(), DateUtils.CACHE_ONE_MONTH)) {
                return Mono.just(existing.get());
            }
            return Mono.zip(
                    fmpService.getIncomeStatement(symbol.toUpperCase(), "annual"),
                    fmpService.getIncomeStatement(symbol.toUpperCase(), "quarter")
            ).flatMap(tuple -> {
                IncomeStatementData incomeStatementData = existing.orElse(new IncomeStatementData());
                incomeStatementData.setSymbol(symbol);
                incomeStatementData.setAnnualReports(FmpDataCleaner.cleanIncomeStatements(tuple.getT1()));
                incomeStatementData.setQuarterlyReports(FmpDataCleaner.cleanIncomeStatements(tuple.getT2()));
                incomeStatementData.setLastUpdated(LocalDateTime.now());
                return Mono.fromCallable(() -> incomeStatementRepository.save(incomeStatementData));
            }).onErrorResume(e -> {
                if (existing.isPresent()) {
                    LOGGER.warn("API call failed for income statements of {}. Falling back to cached data from {}.", 
                                symbol, existing.get().getLastUpdated());
                    return Mono.just(existing.get());
                }
                LOGGER.error("API call failed for income statements of {} and no cached data available.", symbol);
                return Mono.error(e);
            });
        });
    }

    public Mono<BalanceSheetData> getBalanceSheet(String symbol) {
        return Mono.defer(() -> {
            var existing = balanceSheetRepository.findBySymbol(symbol.toUpperCase());
            if (existing.isPresent() && dateUtils.isRecent(existing.get().getLastUpdated(), DateUtils.CACHE_ONE_MONTH)) {
                return Mono.just(existing.get());
            }
            return Mono.zip(
                    fmpService.getBalanceSheetStatement(symbol.toUpperCase(), "annual"),
                    fmpService.getBalanceSheetStatement(symbol.toUpperCase(), "quarter")
            ).flatMap(tuple -> {
                BalanceSheetData balanceSheetData = existing.orElse(new BalanceSheetData());
                balanceSheetData.setSymbol(symbol);
                balanceSheetData.setAnnualReports(FmpDataCleaner.cleanBalanceSheets(tuple.getT1()));
                balanceSheetData.setQuarterlyReports(FmpDataCleaner.cleanBalanceSheets(tuple.getT2()));
                balanceSheetData.setLastUpdated(LocalDateTime.now());
                return Mono.fromCallable(() -> balanceSheetRepository.save(balanceSheetData));
            }).onErrorResume(e -> {
                if (existing.isPresent()) {
                    LOGGER.warn("API call failed for balance sheet of {}. Falling back to cached data from {}.", 
                                symbol, existing.get().getLastUpdated());
                    return Mono.just(existing.get());
                }
                LOGGER.error("API call failed for balance sheet of {} and no cached data available.", symbol);
                return Mono.error(e);
            });
        });
    }

    public Mono<CashFlowData> getCashFlow(String symbol) {
        return Mono.defer(() -> {
            var existing = cashFlowRepository.findBySymbol(symbol.toUpperCase());
            if (existing.isPresent() && dateUtils.isRecent(existing.get().getLastUpdated(), DateUtils.CACHE_ONE_MONTH)) {
                return Mono.just(existing.get());
            }
            return Mono.zip(
                    fmpService.getCashflowStatement(symbol.toUpperCase(), "annual"),
                    fmpService.getCashflowStatement(symbol.toUpperCase(), "quarter")
            ).flatMap(tuple -> {
                CashFlowData cashFlowData = existing.orElse(new CashFlowData());
                cashFlowData.setSymbol(symbol);
                cashFlowData.setAnnualReports(FmpDataCleaner.cleanCashFlows(tuple.getT1()));
                cashFlowData.setQuarterlyReports(FmpDataCleaner.cleanCashFlows(tuple.getT2()));
                cashFlowData.setLastUpdated(LocalDateTime.now());
                return Mono.fromCallable(() -> cashFlowRepository.save(cashFlowData));
            }).onErrorResume(e -> {
                if (existing.isPresent()) {
                    LOGGER.warn("API call failed for cash flow of {}. Falling back to cached data from {}.", 
                                symbol, existing.get().getLastUpdated());
                    return Mono.just(existing.get());
                }
                LOGGER.error("API call failed for cash flow of {} and no cached data available.", symbol);
                return Mono.error(e);
            });
        });
    }

    public Mono<RevenueSegmentationData> getRevenueSegmentation(String symbol) {
        return Mono.defer(() -> {
            var existing = revenueSegmentationDataRepository.findBySymbol(symbol.toUpperCase());
            if (existing.isPresent() && dateUtils.isRecent(existing.get().getLastUpdated(), DateUtils.CACHE_ONE_MONTH)) {
                return Mono.just(existing.get());
            }
            return fmpService.getRevenueSegmentation(symbol.toUpperCase(), "annual")
                    .flatMap(annualReports -> {
                        RevenueSegmentationData data = existing.orElse(new RevenueSegmentationData());
                        data.setSymbol(symbol);
                        data.setAnnualReports(annualReports);
                        data.setLastUpdated(LocalDateTime.now());
                        return Mono.fromCallable(() -> revenueSegmentationDataRepository.save(data));
                    })
                    .onErrorResume(e -> {
                        if (existing.isPresent()) {
                            LOGGER.warn("API call failed for revenue segmentation of {}. Falling back to cached data from {}.", 
                                        symbol, existing.get().getLastUpdated());
                            return Mono.just(existing.get());
                        }
                        LOGGER.error("API call failed for revenue segmentation of {} and no cached data available.", symbol);
                        return Mono.error(e);
                    });
        });
    }

    public Mono<RevenueGeographicSegmentationData> getRevenueGeographicSegmentation(String symbol) {
        return Mono.defer(() -> {
            var existing = revenueGeographicSegmentationRepository.findBySymbol(symbol.toUpperCase());
            if (existing.isPresent() && dateUtils.isRecent(existing.get().getLastUpdated(), DateUtils.CACHE_ONE_MONTH)) {
                return Mono.just(existing.get());
            }
            return fmpService.getRevenueGeographicSegmentation(symbol.toUpperCase(), "annual")
                    .flatMap(reports -> {
                        RevenueGeographicSegmentationData data = existing.orElse(new RevenueGeographicSegmentationData());
                        data.setSymbol(symbol);
                        data.setReports(reports);
                        data.setLastUpdated(LocalDateTime.now());
                        return Mono.fromCallable(() -> revenueGeographicSegmentationRepository.save(data));
                    })
                    .onErrorResume(e -> {
                        if (existing.isPresent()) {
                            LOGGER.warn("API call failed for revenue geographic segmentation of {}. Falling back to cached data from {}.", 
                                        symbol, existing.get().getLastUpdated());
                            return Mono.just(existing.get());
                        }
                        LOGGER.error("API call failed for revenue geographic segmentation of {} and no cached data available.", symbol);
                        return Mono.error(e);
                    });
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