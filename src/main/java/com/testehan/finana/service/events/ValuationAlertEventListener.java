package com.testehan.finana.service.events;

import com.testehan.finana.model.valuation.Valuations;
import com.testehan.finana.model.valuation.dcf.DcfOutput;
import com.testehan.finana.model.valuation.dcf.DcfValuation;
import com.testehan.finana.model.valuation.dcf.ReverseDcfOutput;
import com.testehan.finana.model.valuation.dcf.ReverseDcfValuation;
import com.testehan.finana.model.valuation.growth.GrowthOutput;
import com.testehan.finana.model.valuation.growth.GrowthValuation;
import com.testehan.finana.repository.ValuationsRepository;
import com.testehan.finana.service.ValuationAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ValuationAlertEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValuationAlertEventListener.class);

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ValuationsRepository valuationsRepository;

    public ValuationAlertEventListener(ValuationsRepository valuationsRepository) {
        this.valuationsRepository = valuationsRepository;
    }

    public List<SseEmitter> getEmitters(String userId) {
        return emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
    }

    public void addEmitter(String userId, SseEmitter emitter) {
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> {
            LOGGER.warn("SSE error for user {}: {}", userId, e.getMessage());
            removeEmitter(userId, emitter);
        });
    }

    public void sendInitialAlerts(String userId) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        try {
            List<ValuationAlert> initialAlerts = getAllGeneratedAlerts();
            for (SseEmitter emitter : userEmitters) {
                try {
                    for (ValuationAlert alert : initialAlerts) {
                        emitter.send(SseEmitter.event()
                                .name("VALUATION_ALERT")
                                .data(alert));
                    }
                    emitter.send(SseEmitter.event()
                            .name("INIT_COMPLETE")
                            .data("done"));
                } catch (IOException e) {
                    LOGGER.warn("Client disconnected, removing emitter for user {}: {}", userId, e.getMessage());
                    removeEmitter(userId, emitter);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to send initial alerts to user {}: {}", userId, e.getMessage());
        }
    }

    private List<ValuationAlert> getAllGeneratedAlerts() {
        List<ValuationAlert> alerts = new ArrayList<>();
        List<Valuations> allValuations = valuationsRepository.findAll();

        for (Valuations valuations : allValuations) {
            String ticker = valuations.getTicker();

            for (DcfValuation dcf : valuations.getDcfValuations()) {
                if ("Generated".equals(dcf.getDcfUserInput().getUserComments())) {
                    DcfOutput output = dcf.getDcfOutput();
                    var meta = dcf.getDcfCalculationData().meta();
                    alerts.add(ValuationAlert.fromValuation(
                            ticker, "DCF", output.verdict(),
                            meta.currentSharePrice(), output.intrinsicValuePerShare(), dcf));
                }
            }

            for (GrowthValuation growth : valuations.getGrowthValuations()) {
                if ("Generated".equals(growth.getGrowthUserInput().getUserComments())) {
                    GrowthOutput output = growth.getGrowthOutput();
                    alerts.add(ValuationAlert.fromValuation(
                            ticker, "Growth", output.getVerdict(),
                            growth.getGrowthValuationData().getCurrentSharePrice(), output.getIntrinsicValuePerShare(), growth));
                }
            }

            for (ReverseDcfValuation reverse : valuations.getReverseDcfValuations()) {
                if ("Generated".equals(reverse.getReverseDcfUserInput().getUserComments())) {
                    ReverseDcfOutput output = reverse.getReverseDcfOutput();
                    var meta = reverse.getDcfCalculationData().meta();
                    alerts.add(ValuationAlert.fromValuation(
                            ticker, "Reverse DCF", output.verdict(),
                            meta.currentSharePrice(), null, reverse));
                }
            }
        }

        return alerts;
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
        LOGGER.debug("Removed SSE emitter for user: {}", userId);
    }

    @EventListener
    public void handleValuationAlertCreated(ValuationAlertCreatedEvent event) {
        ValuationAlert alert = event.getAlert();
        String userId = "dante";
        
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("VALUATION_ALERT")
                        .data(alert));
            } catch (IOException e) {
                LOGGER.warn("Failed to send SSE to user {}: {}", userId, e.getMessage());
                removeEmitter(userId, emitter);
            }
        }
    }
}
