package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Manifesto da tabela ou do resumo de eventos de chuva (ADR 0017). Dois datasets com regras diferentes se distinguem
 * por aqui: todas as regras e parâmetros usados entram em {@code params}.
 */
@Schema(description = "Manifesto: datasetId = SHA-256 do CSV, regras, fontes e execuções de ingestão")
public record RainEventManifestDTO(
        String datasetId,
        @Schema(description = "RAIN_EVENTS ou RAIN_EVENT_SUMMARY") String kind,
        String methodVersion,
        String codeVersion,
        List<String> slugs,
        Instant periodFrom,
        Instant periodTo,
        String timezone,
        String displayTimezone,
        String source,
        String sourceTz,
        String sourceVerification,
        Map<String, Object> params,
        List<StationUsed> stations,
        List<ExportManifestDTO.SourceProvenance> provenance,
        Long terrainRunId,
        Long occurrenceLinkageConfigId,
        int rows,
        int truncatedRows,
        int rowsWithGap,
        List<String> rules,
        List<String> limitations,
        List<String> validationOnlyColumns,
        String circularityWarning) {

    /** Estação usada por ponto e papel (rain, river). */
    public record StationUsed(String floodPointSlug, String role, String source, String stationId, Double distanceM) {}
}
