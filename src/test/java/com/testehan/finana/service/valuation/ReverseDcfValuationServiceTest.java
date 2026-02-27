package com.testehan.finana.service.valuation;

import com.testehan.finana.model.valuation.Valuations;
import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.ReverseDcfUserInput;
import com.testehan.finana.model.valuation.dcf.ReverseDcfValuation;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.FMPService;
import com.testehan.finana.service.valuation.dcf.ReverseDCFValuationCalculator;
import com.testehan.finana.util.SafeParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReverseDcfValuationServiceTest {

    private ReverseDcfValuationService service;

    @Mock private CompanyOverviewRepository companyOverviewRepository;
    @Mock private StockQuotesRepository stockQuotesRepository;
    @Mock private IncomeStatementRepository incomeStatementRepository;
    @Mock private BalanceSheetRepository balanceSheetRepository;
    @Mock private CashFlowRepository cashFlowRepository;
    @Mock private ValuationsRepository valuationsRepository;
    @Mock private FMPService fmpService;
    @Mock private SafeParser safeParser;
    @Mock private ReverseDCFValuationCalculator reverseDCFValuationCalculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ReverseDcfValuationService(
                companyOverviewRepository, stockQuotesRepository, incomeStatementRepository,
                balanceSheetRepository, cashFlowRepository, valuationsRepository,
                fmpService, safeParser, reverseDCFValuationCalculator
        );
    }

    @Test
    void calculateReverseDcfValuation_callsCalculator() {
        DcfCalculationData data = DcfCalculationData.builder()
                .meta(DcfCalculationData.CompanyMeta.builder()
                        .ticker("AAPL")
                        .companyName("Apple Inc.")
                        .sector("Tech")
                        .currency("USD")
                        .currentSharePrice(BigDecimal.ONE)
                        .sharesOutstanding(BigDecimal.ONE)
                        .lastUpdated(LocalDate.now())
                        .build())
                .income(DcfCalculationData.IncomeData.builder()
                        .revenue(BigDecimal.ONE)
                        .ebit(BigDecimal.ONE)
                        .interestExpense(BigDecimal.ONE)
                        .incomeTaxExpense(BigDecimal.ONE)
                        .build())
                .balanceSheet(DcfCalculationData.BalanceSheetData.builder()
                        .totalCashAndEquivalents(BigDecimal.ONE)
                        .totalShortTermDebt(BigDecimal.ONE)
                        .totalLongTermDebt(BigDecimal.ONE)
                        .totalCurrentAssets(BigDecimal.ONE)
                        .totalCurrentLiabilities(BigDecimal.ONE)
                        .build())
                .cashFlow(DcfCalculationData.CashFlowData.builder()
                        .operatingCashFlow(BigDecimal.ONE)
                        .depreciationAndAmortization(BigDecimal.ONE)
                        .capitalExpenditure(BigDecimal.ONE.negate())
                        .stockBasedCompensation(BigDecimal.ONE)
                        .build())
                .assumptions(DcfCalculationData.HistoricalAssumptions.builder()
                        .beta(1.0)
                        .riskFreeRate(0.04)
                        .marketRiskPremium(0.05)
                        .effectiveTaxRate(0.2)
                        .revenueGrowthCagr3Year(0.1)
                        .averageEbitMargin3Year(0.2)
                        .fcfGrowthRate(0.1)
                        .marketCapToFcfMultiple(20)
                        .build())
                .build();
        ReverseDcfUserInput input = new ReverseDcfUserInput();
        
        service.calculateReverseDcfValuation(data, input);
        
        verify(reverseDCFValuationCalculator).calculateImpliedGrowthRate(data, input);
    }

    @Test
    void saveReverseDcfValuation_savesToRepository() {
        ReverseDcfValuation valuation = mock(ReverseDcfValuation.class);
        DcfCalculationData data = DcfCalculationData.builder()
                .meta(DcfCalculationData.CompanyMeta.builder().ticker("AAPL").build())
                .build();
        
        when(valuation.getDcfCalculationData()).thenReturn(data);
        when(valuationsRepository.findById("AAPL")).thenReturn(Optional.empty());

        service.saveReverseDcfValuation(valuation);

        verify(valuationsRepository).save(any(Valuations.class));
    }
}
