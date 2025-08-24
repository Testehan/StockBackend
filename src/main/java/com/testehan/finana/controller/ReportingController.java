package com.testehan.finana.controller;

import com.testehan.finana.service.reporting.FerolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/stocks/reporting")
public class ReportingController {

    private final FerolService ferolService;

    @Autowired
    public ReportingController(FerolService ferolService) {
        this.ferolService = ferolService;
    }

    @GetMapping(value = "/ferol/{ticker}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getFerolReport(@PathVariable String ticker, @RequestParam(defaultValue = "false") boolean recreateReport) {
        return ferolService.getFerolReport(ticker.toUpperCase(), recreateReport);
    }
}
