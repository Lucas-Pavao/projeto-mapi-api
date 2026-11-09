package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** Inventário de rótulos depois da deduplicação entre fontes (ADR 0019). Contagens sobre ocorrências canônicas. */
@Schema(description = "Rótulos por fonte, precisão e confiança; grupos deduplicados; casamento por ponto")
public record LabelInventoryDTO(
        String clusterRuleVersion,
        double clusterMaxDistanceM,
        Map<String, Integer> bySource,
        Map<String, Integer> byTemporalPrecision,
        Map<String, Integer> bySpatialPrecision,
        Map<String, Integer> byConfidence,
        int clusters,
        @Schema(description = "Dias locais distintos com pelo menos um grupo") int days,
        @Schema(description = "Dias com grupo de precisão HOUR ou MINUTE") int daysWithHour,
        int daysConfidenceAlta,
        @Schema(description = "Raio do casamento ocorrência↔ponto usado em perPoint (m); nulo sem linkage_config")
        Double pointRadiusM,
        List<PointLabels> perPoint,
        String circularityWarning) {

    public record PointLabels(String floodPointSlug, int occurrences, int days, int daysWithHour) {}
}
