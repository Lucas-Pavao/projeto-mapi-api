package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Acumulados medidos de uma estação canônica, calculados pela PED do Cemaden a partir do pluviômetro (ADR 0023).
 * Acumulado nulo = sem leitura na janela; nunca é zero de chuva.
 */
@Schema(description = "Acumulado medido por estação canônica. Valores em mm; nulo = sem dado na janela (não é zero).")
public record StationAccumulationDTO(
        String canonicalId,
        @Schema(description = "Código da estação devolvido pela PED") String stationCode,
        String name,
        String municipality,
        @Schema(description = "Nulo se a estação não está no cadastro do MAPi") Double lat,
        Double lon,
        @Schema(description = "Identificadores de origem, SOURCE:station_id") List<String> aliases,
        @Schema(description = "OK = algum acumulado com valor; NO_DATA = estação sem leitura em todas as janelas "
                + "ou ausente da resposta da PED", allowableValues = {"OK", "NO_DATA"}) String status,
        @Schema(description = "Última leitura de chuva gravada pelo MAPi para esta estação (UTC), entre as duas fontes; "
                + "nulo se não há leitura nos últimos 5 dias") Instant lastReadingUtc,
        Double acc1hMm,
        Double acc3hMm,
        Double acc6hMm,
        Double acc12hMm,
        Double acc24hMm,
        Double acc48hMm,
        Double acc72hMm,
        Double acc96hMm,
        Double acc120hMm) {
}
