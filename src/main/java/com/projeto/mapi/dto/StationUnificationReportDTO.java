package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** Resultado da unificação de estações (ADR 0023). */
@Schema(description = "Tabela da unificação: canônicas, origem, unificadas e divergências de valor")
public record StationUnificationReportDTO(
        String ruleVersion,
        @Schema(description = "Distância máxima para considerar a mesma estação, m") double maxDistanceM,
        @Schema(description = "Diferença horária acima da qual a hora é registrada como divergência, mm") double toleranceMm,
        @Schema(description = "Estações de origem lidas, por fonte") Map<String, Integer> stationsBySource,
        int canonicalStations,
        @Schema(description = "Canônicas com apelido nas duas fontes") int unified,
        @Schema(description = "Unificadas com o mesmo código nas duas fontes") int unifiedSameCode,
        @Schema(description = "Canônicas só da PED") int pedOnly,
        @Schema(description = "Canônicas só da coleta ao vivo") int liveOnly,
        @Schema(description = "Horas com valor nas duas fontes para a mesma canônica") int hoursCompared,
        @Schema(description = "Horas com diferença acima da tolerância") int divergences,
        Double maxDiffMm,
        @Schema(description = "Cada fusão, para auditoria") List<Merge> merges) {

    public record Merge(String canonicalId, String source, String stationId, String sourceName, String canonicalName,
                        double distanceM, Double nameSimilarity) {}
}
