package com.projeto.mapi.service.warning;

import com.projeto.mapi.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Coleta periódica (a cada 3 h por padrão) da previsão oficial do INMET por município (prompt dos horizontes, bloco A). */
@Component
@RequiredArgsConstructor
@Slf4j
public class InmetForecastSyncTask {

    private final OfficialForecastService service;
    private final AppProperties props;

    @Scheduled(fixedRateString = "${app.inmet.forecast-fixed-rate-ms:10800000}",
            initialDelayString = "${app.inmet.forecast-initial-delay-ms:90000}")
    public void collect() {
        if (!props.getInmet().isEnabled() || !props.getInmet().isForecastEnabled()) return;
        MDC.put("requestId", "inmet-prev-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            int n = service.syncAll();
            log.debug("Previsão do INMET: {} versão(ões) nova(s)", n);
        } catch (Exception e) {
            log.warn("Falha na coleta agendada da previsão do INMET: {}", e.getMessage());
        } finally {
            MDC.remove("requestId");
        }
    }
}
