package com.projeto.mapi.service.tide;

import com.projeto.mapi.dto.TideSyncSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Auto-recuperação: se a tábua de maré local não tiver dados para o ano corrente (primeiro boot,
 * ou virada de ano), sincroniza automaticamente a partir da TabuaMare/DHN. Roda uma vez por dia —
 * a checagem em si é uma query local rápida; só dispara chamadas externas quando faltar dado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TideTableSyncTask {

    private final TideTableSyncService tideTableSyncService;

    @Scheduled(fixedRate = 86400000L, initialDelay = 30000L)
    public void syncIfMissing() {
        String correlationId = "tide-sync-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", correlationId);
        try {
            TideSyncSummaryDTO summary = tideTableSyncService.syncCurrentYearIfMissing();
            if (summary.harborsSynced() > 0) {
                log.info("Tábua de maré local sincronizada: {} portos, {} meses, {} erros.",
                        summary.harborsSynced(), summary.monthsSynced(), summary.errors());
            }
        } catch (Exception e) {
            log.error("Falha na sincronização automática da tábua de maré: {}", e.getMessage(), e);
        } finally {
            MDC.remove("requestId");
        }
    }
}
