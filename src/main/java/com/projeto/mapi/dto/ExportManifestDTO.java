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
        List<String> sourcesNotMigrated,
        List<String> deprecatedColumns,
        LabelManifest label) {

    /** Como o rótulo foi construído (MODELAGEM-IA A.1.4 e A.3.1) e quantos positivos, negativos e excluídos saíram. */
    public record LabelManifest(
            String policy,
            List<Integer> horizonsHours,
            /** G por horizonte (horas); vale G = H quando a propriedade não está fixada. */
            Map<Integer, Integer> postEventWindowHours,
            Long linkageConfigId,
            Double linkageRadiusM,
            String linkageMethod,
            Map<String, LabelCounts> counts,
            Map<String, Integer> excludedByReason,
            int dayLabelPositives,
            /** Ocorrências casadas que tocam o período do export, por precisão. */
            int occurrencesHour,
            int occurrencesDay) {}

    /** Linhas (ponto x hora) por horizonte: rótulo 1, rótulo 0 e excluídas (vazio). */
    public record LabelCounts(int positives, int negatives, int excluded) {}

    /** run_ids que produziram os valores de uma fonte e variável no período. */
    public record SourceProvenance(String source, String variable, List<UUID> runIds, int maxRevision) {}
}
