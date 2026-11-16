package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Previsão de chuva de um ponto. O alvo do modelo é chuva MEDIDA: não leva etiqueta de dado sintético.
 * DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC.
 */
@Schema(description = "Chuva prevista no ponto, por horizonte: faixa entre quantis e probabilidade de evento")
public record RainForecastDTO(
        Long floodPointId,
        String slug,
        Instant issuedAt,
        String modelVersion,
        List<String> sources,
        List<String> missingInputs,
        Instant latestReadingUtc,
        Double latestReadingAgeMinutes,
        List<Horizon> horizons,
        String attribution) {

    public static final String ATTRIBUTION = "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC";

    @Schema(description = "Acumulado previsto em (issuedAt, validUntil], em mm")
    public record Horizon(int horizonHours, Instant validUntil, double q50Mm, double q80Mm, double q95Mm,
                          double probMedia, double probForte, boolean forteAlert, String model) {
    }
}
