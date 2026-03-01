package com.testehan.finana.service;

import com.testehan.finana.service.events.ValuationAlertCreatedEvent;
import com.testehan.finana.service.events.ValuationAlertEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ValuationAlertServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ValuationAlertEventListener eventListener;

    @InjectMocks
    private ValuationAlertService valuationAlertService;

    @Test
    void subscribe_addsEmitterAndSendsInitialAlerts() {
        String userId = "user123";
        
        SseEmitter emitter = valuationAlertService.subscribe(userId);
        
        assertNotNull(emitter);
        verify(eventListener).addEmitter(eq(userId), any(SseEmitter.class));
        verify(eventListener).sendInitialAlerts(userId);
    }

    @Test
    void pushValuationAlert_publishesEvent() {
        ValuationAlert alert = new ValuationAlert();
        
        valuationAlertService.pushValuationAlert(alert);
        
        verify(eventPublisher).publishEvent(any(ValuationAlertCreatedEvent.class));
    }
}
