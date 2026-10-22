package com.projeto.mapi.dto;

import java.time.Instant;

/**
 * Status por fonte. {@code lastRunId} é o UUID do ingestion_run (string): o contrato do YAML dizia inteiro,
 * mas {@code ingestion_run.id} é UUID desde a V3.
 */
public record IngestionSourceStatusDTO(String source, String status, Integer expectedEveryMinutes,
                                       Instant lastSuccessAt, String lastRunId, String lastError, long rowsLast24h) {
}
