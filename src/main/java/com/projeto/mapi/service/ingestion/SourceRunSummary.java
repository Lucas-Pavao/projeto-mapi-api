package com.projeto.mapi.service.ingestion;

import java.time.Instant;
import java.util.UUID;

/** Resumo de runs de uma fonte (base de GET /api/admin/ingestion/sources). */
public record SourceRunSummary(String source, UUID lastRunId, String lastStatus, String lastError,
                               Integer lastValid, Integer lastQuarantined, Instant lastSuccessAt, long rowsLast24h) {
}
