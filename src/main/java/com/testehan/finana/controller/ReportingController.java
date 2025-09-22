package com.testehan.finana.controller;

import com.testehan.finana.service.reporting.FerolReportOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
}
