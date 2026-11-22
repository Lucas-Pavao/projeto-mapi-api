package com.projeto.mapi.service.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Confere a cada hora se algum modelo do Open-Meteo publicou rodada nova e grava (prompt dos horizontes, bloco B). */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenMeteoModelSyncTask {

    private final OpenMeteoModelService service;

    @Value("${app.open-meteo-models.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedRateString = "${app.open-meteo-models.fixed-rate-ms:3600000}",
            initialDelayString = "${app.open-meteo-models.initial-delay-ms:120000}")
    public void collect() {
        if (!enabled) return;
        MDC.put("requestId", "om-modelos-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            int n = service.collectLive();
            log.debug("Open-Meteo por modelo: {} hora(s) nova(s)", n);
        } catch (Exception e) {
            log.warn("Falha na coleta do Open-Meteo por modelo: {}", e.getMessage());
        } finally {
            MDC.remove("requestId");
        }
    }
}
