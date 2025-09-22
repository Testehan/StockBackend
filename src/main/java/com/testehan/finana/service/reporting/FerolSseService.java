package com.testehan.finana.service.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class FerolSseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FerolSseService.class);

    public void sendSseEvent(SseEmitter sseEmitter, String message) {
        try {
            sseEmitter.send(SseEmitter.event().name("MESSAGE").data(message));
            LOGGER.info("SSE Event sent: {}", message);
        } catch (Exception e) {
            LOGGER.error("Error sending SSE event: {}", e.getMessage());
            // Don't completeWithError here, as it might be a temporary network issue.
            // Let the main try-catch handle the completion if the core task fails.
        }
    }

    public void sendSseErrorEvent(SseEmitter sseEmitter, String message) {
        try {
            sseEmitter.send(SseEmitter.event().name("ERROR").data(message));
            LOGGER.error("SSE Error Event sent: {}", message);
        } catch (Exception e) {
            LOGGER.error("Error sending SSE error event: {}", e.getMessage());
        }
    }
}
