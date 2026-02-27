package com.testehan.finana.controller;

import com.testehan.finana.model.reporting.ChecklistReport;
import com.testehan.finana.model.reporting.ChecklistReportSummaryDTO;
import com.testehan.finana.model.reporting.ReportType;
import com.testehan.finana.repository.UserStockRepository;
import com.testehan.finana.service.reporting.ChecklistReportOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingController.class)
public class ReportingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChecklistReportOrchestrator checklistReportOrchestrator;

    @MockitoBean
    private UserStockRepository userStockRepository;

    @Test
    public void testGetChecklistReportsSummary() throws Exception {
        Page<ChecklistReportSummaryDTO> page = new PageImpl<>(new ArrayList<>());
        when(checklistReportOrchestrator.getChecklistReportsSummary(any(Pageable.class), any())).thenReturn(page);
        when(userStockRepository.findByUserId(anyString())).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/stocks/reporting/checklist/summary/user123"))
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveChecklistReport() throws Exception {
        ChecklistReport report = new ChecklistReport();
        when(checklistReportOrchestrator.saveChecklistReport(anyString(), anyList(), any(ReportType.class))).thenReturn(report);

        String body = "[]";

        mockMvc.perform(post("/stocks/reporting/checklist/AAPL")
                .param("reportType", "FEROL")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
    }
}
