package com.projeto.mapi.service.warning;

import com.projeto.mapi.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Coleta periódica (a cada 10 min por padrão) dos avisos meteorológicos ativos do INMET (ADR 0024). */
@Component
@RequiredArgsConstructor
@Slf4j
public class InmetWarningSyncTask {

    private final OfficialWarningService service;
    private final AppProperties props;

    @Scheduled(fixedRateString = "${app.inmet.fixed-rate-ms:600000}",
            initialDelayString = "${app.inmet.initial-delay-ms:45000}")
    public void collect() {
        if (!props.getInmet().isEnabled()) return;
        MDC.put("requestId", "inmet-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            int count = service.syncInmetActive();
            log.debug("Avisos INMET sincronizados: {} aviso(s) na área monitorada", count);
        } catch (Exception e) {
            log.warn("Falha na coleta agendada de avisos do INMET: {}", e.getMessage());
        } finally {
            MDC.remove("requestId");
        }
    }
}
