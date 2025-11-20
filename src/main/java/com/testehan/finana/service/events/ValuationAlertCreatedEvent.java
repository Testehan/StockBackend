package com.testehan.finana.service.events;

import com.testehan.finana.service.ValuationAlert;
import org.springframework.context.ApplicationEvent;

public class ValuationAlertCreatedEvent extends ApplicationEvent {

    private final ValuationAlert alert;

    public ValuationAlertCreatedEvent(Object source, ValuationAlert alert) {
        super(source);
        this.alert = alert;
    }

    public ValuationAlert getAlert() {
        return alert;
    }
}
