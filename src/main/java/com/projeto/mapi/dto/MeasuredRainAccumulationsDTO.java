package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Resposta dos acumulados medidos do Cemaden por estação canônica (ADR 0023). */
@Schema(description = "Acumulados de chuva MEDIDA (pluviômetro do Cemaden), por estação canônica. Não é reanálise.")
public record MeasuredRainAccumulationsDTO(
        @Schema(description = "Instante do cálculo na PED (campo datahora), UTC") Instant computedAtUtc,
        @Schema(description = "Quando o MAPi buscou na PED, UTC") Instant fetchedAtUtc,
        @Schema(description = "true se a última busca bem-sucedida tem mais de 30 min") boolean stale,
        @Schema(description = "Sempre UTC") String timezone,
        @Schema(description = "Sempre mm") String unit,
        @Schema(description = "MEASURED: pluviômetro. A reanálise (Open-Meteo) é REANALYSIS e não vem nesta rota")
        String rainOrigin,
        @Schema(description = "CEMADEN_PED") String source,
        @Schema(description = "Citação obrigatória") String attribution,
        @Schema(description = "Municípios (IBGE) consultados") List<String> municipalitiesQueried,
        @Schema(description = "Municípios com HTTP 202 \"Nenhum resultado\": sem dado, não é erro") List<String> municipalitiesWithoutData,
        @Schema(description = "Municípios cuja consulta falhou no último ciclo (valores do ciclo anterior, se houver)")
        List<String> municipalitiesFailed,
        List<StationAccumulationDTO> stations) {
}
