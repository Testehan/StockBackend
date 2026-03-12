package com.testehan.finana.service;

import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.DcfValuation;
import com.testehan.finana.model.valuation.dcf.ReverseDcfValuation;
import com.testehan.finana.model.valuation.growth.GrowthValuation;
import com.testehan.finana.service.valuation.DcfValuationService;
import com.testehan.finana.service.valuation.GrowthValuationService;
import com.testehan.finana.service.valuation.ReverseDcfValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ValuationServiceTest {

    private ValuationService valuationService;

    @Mock private GrowthValuationService growthValuationService;
    @Mock private DcfValuationService dcfValuationService;
    @Mock private ReverseDcfValuationService reverseDcfValuationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        valuationService = new ValuationService(growthValuationService, dcfValuationService, reverseDcfValuationService);
    }

    @Test
    void getGrowthCompanyValuationData_delegatesToGrowthService() {
        String ticker = "AAPL";
        GrowthValuation expected = new GrowthValuation();
        when(growthValuationService.getGrowthCompanyValuationData(ticker)).thenReturn(expected);

        GrowthValuation result = valuationService.getGrowthCompanyValuationData(ticker);

        assertEquals(expected, result);
        verify(growthValuationService).getGrowthCompanyValuationData(ticker);
    }

    @Test
    void getDcfHistory_delegatesToDcfService() {
        String ticker = "AAPL";
        String userEmail = "test@example.com";
        List<DcfValuation> expected = Collections.emptyList();
        when(dcfValuationService.getDcfHistory(ticker, userEmail)).thenReturn(expected);

        List<DcfValuation> result = valuationService.getDcfHistory(ticker, userEmail);

        assertEquals(expected, result);
        verify(dcfValuationService).getDcfHistory(ticker, userEmail);
    }

    @Test
    void deleteGrowthValuation_delegatesToGrowthService() {
        String ticker = "AAPL";
        String date = "2023-01-01";
        String userEmail = "test@example.com";
        when(growthValuationService.deleteGrowthValuation(ticker, date, userEmail)).thenReturn(true);

        boolean result = valuationService.deleteGrowthValuation(ticker, date, userEmail);

        assertEquals(true, result);
        verify(growthValuationService).deleteGrowthValuation(ticker, date, userEmail);
    }
}
