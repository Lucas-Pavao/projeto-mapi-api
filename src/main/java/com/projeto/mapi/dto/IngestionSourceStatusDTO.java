package com.projeto.mapi.dto;

import java.time.Instant;

/**
 * Status por fonte. {@code lastRunId} é o UUID do ingestion_run (string): o contrato do YAML dizia inteiro,
 * mas {@code ingestion_run.id} é UUID desde a V3.
 */
public record IngestionSourceStatusDTO(String source, String status, Integer expectedEveryMinutes,
                                       Instant lastSuccessAt, String lastRunId, String lastError, long rowsLast24h,
                                       Freshness freshness) {

    /**
     * Idade da leitura ACEITA mais nova (V29), por adição. {@code state}: FRESH, STALE (mais velha que o aceito),
     * NO_DATA (nenhuma leitura aceita em 60 dias), COVERAGE_LOW (previsão cobre menos que o mínimo à frente) ou
     * UNMONITORED (fonte sem limite: carga manual).
     */
    public record Freshness(String state, Instant lastAcceptedAt, Long ageMinutes, Integer maxAgeMinutes,
                            Instant coverageUntil, Integer minCoverageAheadMinutes) {
    }
}
