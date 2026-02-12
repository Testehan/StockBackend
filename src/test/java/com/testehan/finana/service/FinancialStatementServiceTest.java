package com.testehan.finana.service;

import com.testehan.finana.model.finstatement.BalanceSheetData;
import com.testehan.finana.model.finstatement.CashFlowData;
import com.testehan.finana.model.finstatement.IncomeStatementData;
import com.testehan.finana.repository.*;
import com.testehan.finana.util.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialStatementServiceTest {

    @Mock
    private FMPService fmpService;
    @Mock
    private IncomeStatementRepository incomeStatementRepository;
    @Mock
    private BalanceSheetRepository balanceSheetRepository;
    @Mock
    private CashFlowRepository cashFlowRepository;
    @Mock
    private RevenueSegmentationDataRepository revenueSegmentationDataRepository;
    @Mock
    private RevenueGeographicSegmentationRepository revenueGeographicSegmentationRepository;
    @Mock
    private DateUtils dateUtils;

    @InjectMocks
    private FinancialStatementService financialStatementService;

    private final String SYMBOL = "AAPL";

    @Test
    void getIncomeStatements_whenInDbAndRecent_returnsFromDb() {
        IncomeStatementData data = new IncomeStatementData();
        data.setSymbol(SYMBOL);
        data.setLastUpdated(LocalDateTime.now());

        when(incomeStatementRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(data));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_ONE_MONTH))).thenReturn(true);

        StepVerifier.create(financialStatementService.getIncomeStatements(SYMBOL))
                .expectNext(data)
                .verifyComplete();

        verify(fmpService, never()).getIncomeStatement(any(), any());
    }

    @Test
    void getIncomeStatements_whenNotInDb_fetchesFromApi() {
        when(incomeStatementRepository.findBySymbol(SYMBOL)).thenReturn(Optional.empty());
        when(fmpService.getIncomeStatement(SYMBOL, "annual")).thenReturn(Mono.just(new java.util.ArrayList<>()));
        when(fmpService.getIncomeStatement(SYMBOL, "quarter")).thenReturn(Mono.just(new java.util.ArrayList<>()));
        when(incomeStatementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(financialStatementService.getIncomeStatements(SYMBOL))
                .expectNextMatches(data -> data.getSymbol().equals(SYMBOL))
                .verifyComplete();
    }

    @Test
    void getBalanceSheet_whenInDbAndRecent_returnsFromDb() {
        BalanceSheetData data = new BalanceSheetData();
        data.setSymbol(SYMBOL);
        data.setLastUpdated(LocalDateTime.now());

        when(balanceSheetRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(data));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_ONE_MONTH))).thenReturn(true);

        StepVerifier.create(financialStatementService.getBalanceSheet(SYMBOL))
                .expectNext(data)
                .verifyComplete();
    }

    @Test
    void getCashFlow_whenInDbAndRecent_returnsFromDb() {
        CashFlowData data = new CashFlowData();
        data.setSymbol(SYMBOL);
        data.setLastUpdated(LocalDateTime.now());

        when(cashFlowRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(data));
        when(dateUtils.isRecent(any(), eq(DateUtils.CACHE_ONE_MONTH))).thenReturn(true);

        StepVerifier.create(financialStatementService.getCashFlow(SYMBOL))
                .expectNext(data)
                .verifyComplete();
    }
}
