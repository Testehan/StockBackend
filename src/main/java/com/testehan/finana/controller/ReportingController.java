package com.testehan.finana.controller;

import com.testehan.finana.model.FerolReport;
import com.testehan.finana.model.FerolReportItem;
import com.testehan.finana.service.reporting.FerolReportOrchestrator;
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

    private final FerolReportOrchestrator ferolReportOrchestrator;

    @Autowired
    public ReportingController(FerolReportOrchestrator ferolReportOrchestrator) {
        this.ferolReportOrchestrator = ferolReportOrchestrator;
    }

    @GetMapping(value = "/ferol/{ticker}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getFerolReport(@PathVariable String ticker, @RequestParam(defaultValue = "false") boolean recreateReport) {
        return ferolReportOrchestrator.getFerolReport(ticker.toUpperCase(), recreateReport);
    }

    @PostMapping("/ferol/{symbol}")
    public ResponseEntity<FerolReport> saveFerolReport(@PathVariable String symbol, @RequestBody List<FerolReportItem> ferolReportItems) {
        FerolReport savedReport = ferolReportOrchestrator.saveFerolReport(symbol.toUpperCase(), ferolReportItems);
        return new ResponseEntity<>(savedReport, HttpStatus.CREATED);
    }
}
