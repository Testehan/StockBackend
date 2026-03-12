package com.testehan.finana.service.reporting;

import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.model.reporting.ReportType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ReportGenerator {
    void generate(String ticker, ReportType reportType, SseEmitter sseEmitter) throws InterruptedException;
    ReportType getReportType();

    default ReportItem createErrorReportItem(String explanation) {
        return new ReportItem("insufficient-credit", -10, explanation);
    }
}
