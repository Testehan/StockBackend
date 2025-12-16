package com.testehan.finana.service.reporting;

import com.testehan.finana.model.reporting.ReportType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ReportGenerator {
    void generate(String ticker, ReportType reportType, SseEmitter sseEmitter) throws InterruptedException;
    ReportType getReportType();
}
