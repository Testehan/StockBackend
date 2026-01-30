package com.testehan.finana.service;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.finstatement.*;
import com.testehan.finana.model.ratio.FinancialRatiosData;
import com.testehan.finana.model.ratio.FinancialRatiosReport;
import com.testehan.finana.repository.FinancialRatiosRepository;
import com.testehan.finana.repository.GeneratedReportRepository;
import com.testehan.finana.util.DateUtils;
import com.testehan.finana.util.FinancialRatiosCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialDataServiceTest {

    @Mock
    private FMPService fmpService;

    @Mock
    private CompanyDataService companyDataService;

    @Mock
    private FinancialRatiosRepository financialRatiosRepository;

    @Mock
    private GeneratedReportRepository generatedReportRepository;

    @Mock
    private FinancialStatementService financialStatementService;

    @Mock
    private FinancialRatiosCalculator financialRatiosCalculator;

    @Mock
    private QuoteService quoteService;

    @Mock
    private DateUtils dateUtils;

    private FinancialDataService financialDataService;

    @BeforeEach
    void setUp() {
        financialDataService = new FinancialDataService(
                fmpService, companyDataService, financialStatementService,
                financialRatiosRepository, generatedReportRepository,
                financialRatiosCalculator, quoteService, dateUtils
        );
    }

    @Test
    void getFinancialRatios_CacheHit_ReturnsExistingData() {
        String symbol = "AAPL";
        FinancialRatiosData existingData = new FinancialRatiosData();
        existingData.setSymbol(symbol);
        existingData.setLastUpdated(LocalDateTime.now());

        when(financialRatiosRepository.findBySymbol(symbol)).thenReturn(Optional.of(existingData));
        when(dateUtils.isRecent(any(), anyInt())).thenReturn(true);

        Mono<Optional<FinancialRatiosData>> result = financialDataService.getFinancialRatios(symbol);

        assertTrue(result.block().isPresent());
        assertEquals(symbol, result.block().get().getSymbol());
    }

    @Test
    void hasFinancialRatios_Exists_ReturnsTrue() {
        String symbol = "AAPL";
        when(financialRatiosRepository.findBySymbol(symbol)).thenReturn(Optional.of(new FinancialRatiosData()));

        boolean result = financialDataService.hasFinancialRatios(symbol);

        assertTrue(result);
    }

    @Test
    void hasFinancialRatios_NotExists_ReturnsFalse() {
        String symbol = "AAPL";
        when(financialRatiosRepository.findBySymbol(symbol)).thenReturn(Optional.empty());

        boolean result = financialDataService.hasFinancialRatios(symbol);

        assertFalse(result);
    }

    @Test
    void hasGeneratedReport_Exists_ReturnsTrue() {
        String symbol = "AAPL";
        when(generatedReportRepository.findBySymbol(symbol)).thenReturn(Optional.of(new com.testehan.finana.model.reporting.GeneratedReport()));

        boolean result = financialDataService.hasGeneratedReport(symbol);

        assertTrue(result);
    }

    @Test
    void hasGeneratedReport_NotExists_ReturnsFalse() {
        String symbol = "AAPL";
        when(generatedReportRepository.findBySymbol(symbol)).thenReturn(Optional.empty());

        boolean result = financialDataService.hasGeneratedReport(symbol);

        assertFalse(result);
    }
}