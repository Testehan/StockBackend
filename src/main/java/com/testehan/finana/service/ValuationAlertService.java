package com.testehan.finana.service;

import com.testehan.finana.service.events.ValuationAlertCreatedEvent;
import com.testehan.finana.service.events.ValuationAlertEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ValuationAlertService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValuationAlertService.class);

    private final ApplicationEventPublisher eventPublisher;
    private final ValuationAlertEventListener eventListener;

    public ValuationAlertService(ApplicationEventPublisher eventPublisher,
                                ValuationAlertEventListener eventListener) {
        this.eventPublisher = eventPublisher;
        this.eventListener = eventListener;
    }

    public SseEmitter subscribe(String userEmail) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        eventListener.addEmitter(userEmail, emitter);
        eventListener.sendInitialAlerts(userEmail);
        
        LOGGER.info("New SSE subscription for user: {}", userEmail);
        
        return emitter;
    }

    public void pushValuationAlert(ValuationAlert alert) {
        eventPublisher.publishEvent(new ValuationAlertCreatedEvent(this, alert));
    }
}
