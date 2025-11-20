package com.testehan.finana.service.reporting.events;

import com.testehan.finana.model.reporting.ChecklistReport;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class CompletionEvent extends ReportEvent {
    private final ChecklistReport checklistReport;

    public CompletionEvent(Object source, String ticker, SseEmitter sseEmitter, ChecklistReport checklistReport) {
        super(source, ticker, sseEmitter);
        this.checklistReport = checklistReport;
    }

    public ChecklistReport getChecklistReport() {
        return checklistReport;
    }
}
