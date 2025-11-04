package com.testehan.finana.service.reporting.calc;

import com.testehan.finana.model.ReportItem;
import com.testehan.finana.model.ReportType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;

@FunctionalInterface
public interface ReportItemCalculator {
    Collection<ReportItem> calculate(String ticker, ReportType reportType, SseEmitter sseEmitter);
}
