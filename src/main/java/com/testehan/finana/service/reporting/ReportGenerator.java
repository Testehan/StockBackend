package com.testehan.finana.service.reporting;

import com.testehan.finana.model.reporting.ReportType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public interface ReportGenerator {
    void generate(String ticker, ReportType reportType, SseEmitter sseEmitter) throws InterruptedException, ExecutionException, IOException;
    ReportType getReportType();
}
