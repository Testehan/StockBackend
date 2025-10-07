package com.testehan.finana.controller;

import com.testehan.finana.model.ChecklistReport;
import com.testehan.finana.model.ChecklistReportSummaryDTO;
import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.ReportType;
import com.testehan.finana.service.reporting.ChecklistReportOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/stocks/reporting")
public class ReportingController {

    private final ChecklistReportOrchestrator checklistReportOrchestrator;

    @Autowired
    public ReportingController(ChecklistReportOrchestrator checklistReportOrchestrator) {
        this.checklistReportOrchestrator = checklistReportOrchestrator;
    }

    @GetMapping(value = "/checklist/{ticker}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getChecklistReport(@PathVariable String ticker, @RequestParam(defaultValue = "false") boolean recreateReport, @RequestParam ReportType reportType) {
        return checklistReportOrchestrator.getChecklistReport(ticker.toUpperCase(), recreateReport, reportType);
    }

    @PostMapping("/checklist/{symbol}")
    public ResponseEntity<ChecklistReport> saveChecklistReport(@PathVariable String symbol, @RequestBody List<ReportItem> reportItems, @RequestParam ReportType reportType) {
        ChecklistReport savedReport = checklistReportOrchestrator.saveChecklistReport(symbol.toUpperCase(), reportItems, reportType);
        return new ResponseEntity<>(savedReport, HttpStatus.CREATED);
    }

    @GetMapping("/checklist/summary")
    public ResponseEntity<List<ChecklistReportSummaryDTO>> getChecklistReportsSummary() {
        List<ChecklistReportSummaryDTO> summary = checklistReportOrchestrator.getChecklistReportsSummary();
        return new ResponseEntity<>(summary, HttpStatus.OK);
    }
}
