package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Acerto da previsão de chuva nos últimos dias, contra a chuva medida (conferência diária)")
public record RainForecastAccuracyDTO(int days, Instant from, Instant to, Instant lastVerifiedAt, long pending,
                                      List<Row> horizons, String attribution) {

    @Schema(description = "pinballMm: erro de quantil médio (50%, 80%, 95%); cobertura: fração das vezes em que a medida ficou até o quantil")
    public record Row(int horizonHours, long forecasts, long withoutMeasure, Double pinballMm, Double coverageQ50,
                      Double coverageQ80, Double coverageQ95, Double meanObservedMm, long forteEvents, long forteAlerts,
                      long forteHits) {
    }
}
