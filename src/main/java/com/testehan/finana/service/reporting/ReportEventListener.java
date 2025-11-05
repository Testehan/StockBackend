package com.testehan.finana.service.reporting;

import com.testehan.finana.service.reporting.events.CompletionEvent;
import com.testehan.finana.service.reporting.events.ErrorEvent;
import com.testehan.finana.service.reporting.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
public class ReportEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportEventListener.class);

    @EventListener
    public void handleMessageEvent(MessageEvent event) {
        SseEmitter emitter = event.getSseEmitter();
        try {
            emitter.send(SseEmitter.event().name("MESSAGE").data(event.getMessage()));
        } catch (IOException e) {
            LOGGER.warn("Failed to send SSE message event for ticker {}: {}", event.getTicker(), e.getMessage());
        }
    }

    @EventListener
    public void handleCompletionEvent(CompletionEvent event) {
        SseEmitter emitter = event.getSseEmitter();
        try {
            emitter.send(SseEmitter.event()
                    .name("COMPLETED")
                    .data(event.getChecklistReport(), MediaType.APPLICATION_JSON));
            emitter.complete();
            LOGGER.info("Checklist report for {} sent and emitter completed.", event.getTicker());
        } catch (IOException e) {
            LOGGER.warn("Failed to send SSE completion event for ticker {}: {}", event.getTicker(), e.getMessage());
            emitter.completeWithError(e);
        }
    }

    @EventListener
    public void handleErrorEvent(ErrorEvent event) {
        SseEmitter emitter = event.getSseEmitter();
        try {
            emitter.send(SseEmitter.event()
                    .name("ERROR")
                    .data(event.getThrowable().getMessage(), MediaType.APPLICATION_JSON));
            LOGGER.error("Error during report generation for ticker {}: {}", event.getTicker(), event.getThrowable().getMessage(), event.getThrowable());
        } catch (IOException e) {
            LOGGER.warn("Failed to send SSE completion event for ticker {}: {}", event.getTicker(), e.getMessage());
            emitter.completeWithError(e);
        }
    }
}
