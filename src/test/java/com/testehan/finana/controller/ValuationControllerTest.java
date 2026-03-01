package com.testehan.finana.controller;

import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.DcfValuation;
import com.testehan.finana.service.ValuationAlertService;
import com.testehan.finana.service.ValuationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ValuationController.class)
public class ValuationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ValuationService valuationService;

    @MockitoBean
    private ValuationAlertService valuationAlertService;

    @Test
    public void testGetDcfValuationData() throws Exception {
        DcfCalculationData.CompanyMeta meta = DcfCalculationData.CompanyMeta.builder()
                .ticker("AAPL")
                .build();
        DcfCalculationData data = DcfCalculationData.builder()
                .meta(meta)
                .build();

        when(valuationService.getDcfCalculationData("AAPL")).thenReturn(data);

        mockMvc.perform(get("/stocks/valuation/dcf/AAPL"))
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveDcfValuation() throws Exception {
        String body = "{\"symbol\": \"AAPL\", \"valuationDate\": \"2023-10-10\"}";

        mockMvc.perform(post("/stocks/valuation/dcf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }
}
