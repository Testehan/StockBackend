package com.testehan.finana.service.reporting.events;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class ErrorEvent extends ReportEvent {
    private final Throwable throwable;

    public ErrorEvent(Object source, String ticker, SseEmitter sseEmitter, Throwable throwable) {
        super(source, ticker, sseEmitter);
        this.throwable = throwable;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
