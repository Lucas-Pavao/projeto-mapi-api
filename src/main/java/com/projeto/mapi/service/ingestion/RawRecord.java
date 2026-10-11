package com.projeto.mapi.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Um registro de origem (estação x instante), como a fonte entregou. O parser só declara
 * {@code sourceTz}; a conversão para UTC é feita na validação (ADR 0006 seção 4).
 */
public record RawRecord(
        String source,
        String stationId,
        Instant collectedAt,
        String sourceTsText,
        String sourceTsNorm,
        String sourceTz,
        JsonNode payload) {

    /** Identidade do registro de origem dentro da fonte: (station_id, source_ts_norm). */
    public OriginKey key() {
        return new OriginKey(stationId, sourceTsNorm);
    }
}
