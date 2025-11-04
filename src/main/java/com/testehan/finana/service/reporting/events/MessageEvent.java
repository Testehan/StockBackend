package com.testehan.finana.service.reporting.events;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class MessageEvent extends ReportEvent {
    private final String message;

    public MessageEvent(Object source, String ticker, SseEmitter sseEmitter, String message) {
        super(source, ticker, sseEmitter);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
