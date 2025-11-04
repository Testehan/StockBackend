package com.testehan.finana.service.reporting.events;

import org.springframework.context.ApplicationEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public abstract class ReportEvent extends ApplicationEvent {
    private final String ticker;
    private final SseEmitter sseEmitter;

    public ReportEvent(Object source, String ticker, SseEmitter sseEmitter) {
        super(source);
        this.ticker = ticker;
        this.sseEmitter = sseEmitter;
    }

    public String getTicker() {
        return ticker;
    }

    public SseEmitter getSseEmitter() {
        return sseEmitter;
    }
}
