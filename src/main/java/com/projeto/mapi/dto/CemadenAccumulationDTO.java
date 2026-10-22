package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * Acumulados de chuva publicados pela própria CEMADEN para uma estação da RMR, lado a lado com a soma dos blocos que o
 * MAPi gravou nas últimas 24 h. Acumulado nulo = a estação não transmitiu dentro da janela (não é "não choveu").
 */
public record CemadenAccumulationDTO(
        String name,
        String municipality,
        /** Código GMMC da estação no MAPi, quando o nome casa com uma estação gravada; senão nulo. */
        String stationId,
        Double lat,
        Double lon,
        int stationType,
        Double lastValueMm,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastReadingUtc,
        Double acc1hMm,
        Double acc3hMm,
        Double acc6hMm,
        Double acc12hMm,
        Double acc24hMm,
        Double acc48hMm,
        Double acc72hMm,
        Double acc96hMm,
        /** Soma das horas de observation_hourly_v com fim nas últimas 24 h; nulo sem estação casada ou sem hora. */
        Double mapiAcc24hMm,
        /** mapiAcc24hMm - acc24hMm (conferência da coleta). */
        Double diff24hMm) {
}
