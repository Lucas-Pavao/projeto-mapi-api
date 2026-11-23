package com.projeto.mapi.service.weather;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Carga do histórico de rodadas do Open-Meteo por modelo na subida (onda 16, Fase 7.7), desligada por padrão. É a mesma
 * carga do {@code POST /api/admin/open-meteo/historico}, retomável e em segundo plano; serve para disparar a carga num
 * implante sem login de administrador ({@code MAPI_OPEN_METEO_HISTORY_LOAD=true} no ambiente, uma vez).
 */
@Component
@Slf4j
public class OpenMeteoHistoryStartupLoader {

    private final OpenMeteoModelService service;
    private final boolean enabled;
    private final LocalDate from;

    public OpenMeteoHistoryStartupLoader(OpenMeteoModelService service,
                                         @Value("${app.open-meteo-models.history-load-on-startup:false}") boolean enabled,
                                         @Value("${app.open-meteo-models.history-from:2024-01-15}") LocalDate from) {
        this.service = service;
        this.enabled = enabled;
        this.from = from;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadIfAsked() {
        if (!enabled) return;
        log.info("Open-Meteo por modelo: carga do histórico desde {} pedida na subida", from);
        service.backfillAsync(from);
    }
}
