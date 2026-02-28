package com.testehan.finana.controller;

import com.testehan.finana.model.adjustment.FinancialAdjustment;
import com.testehan.finana.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
public class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlphaVantageService alphaVantageService;
    @MockitoBean
    private FMPService fmpService;
    @MockitoBean
    private FinancialDataOrchestrator financialDataOrchestrator;
    @MockitoBean
    private CompanyDataService companyDataService;
    @MockitoBean
    private FinancialStatementService financialStatementService;
    @MockitoBean
    private EarningsService earningsService;
    @MockitoBean
    private QuoteService quoteService;
    @MockitoBean
    private FinancialDataService financialDataService;
    @MockitoBean
    private AdjustmentService adjustmentService;

    @Test
    public void testGetFinancialAdjustments() throws Exception {
        FinancialAdjustment adjustment = new FinancialAdjustment();
        when(adjustmentService.getFinancialAdjustments(anyString())).thenReturn(Mono.just(adjustment));

        MvcResult mvcResult = mockMvc.perform(get("/stocks/adjustments/AAPL"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());
    }

    @Test
    public void testDeleteStockData() throws Exception {
        MvcResult mvcResult = mockMvc.perform(delete("/stocks/delete/AAPL"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isNoContent());
    }
}
