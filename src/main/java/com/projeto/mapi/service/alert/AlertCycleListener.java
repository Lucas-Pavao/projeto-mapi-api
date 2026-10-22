package com.projeto.mapi.service.alert;

import com.projeto.mapi.service.flood.PredictionCycleCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Roda o avaliador depois de cada rodada de previsão (ADR 0011). Falha aqui nunca derruba o ciclo. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertCycleListener {

    private final AlertEvaluator evaluator;

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onCycleCompleted(PredictionCycleCompletedEvent event) {
        try {
            evaluator.evaluate();
        } catch (Exception e) {
            log.error("Falha no avaliador de alertas após o ciclo [{}]: {}", event.origin(), e.getMessage(), e);
        }
    }
}
