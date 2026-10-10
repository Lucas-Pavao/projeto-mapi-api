package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manifesto do dataset exportado (ADR 0006 seção 2). {@code datasetId} é o SHA-256 do CSV. Não traz data de
 * geração de propósito: o manifesto é função do conteúdo, para ser reproduzível.
 */
public record ExportManifestDTO(
        String datasetId,
        String slug,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant periodFrom,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant periodTo,
        String timezone,
        String ruleVersion,
        String codeVersion,
        int rows,
        int rowsWithFlag,
        List<SourceProvenance> provenance,
        com.projeto.mapi.service.terrain.TerrainExportColumns.Provenance terrain,
        String missingPolicy,
        Map<String, Integer> missingCounts,
        List<String> sourcesNotMigrated) {

    /** run_ids que produziram os valores de uma fonte e variável no período. */
    public record SourceProvenance(String source, String variable, List<UUID> runIds, int maxRevision) {}
}
