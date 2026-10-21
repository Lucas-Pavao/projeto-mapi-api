package com.projeto.mapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record AlertRuleInputDTO(
        @NotNull(message = "kind é obrigatório")
        @Pattern(regexp = "RISK_LEVEL|RIVER_STAGE|TIDE_ABOVE|RAIN_RATE|SENSOR_STALE|PREDICTION_UNAVAILABLE",
                message = "kind inválido") String kind,
        String floodPointSlug,
        String stationId,
        Double threshold,
        String thresholdSource,
        @Pattern(regexp = "MEDIUM|HIGH", message = "minRisk deve ser MEDIUM ou HIGH") String minRisk,
        @NotNull(message = "enabled é obrigatório") Boolean enabled,
        List<@Pattern(regexp = "BROWSER", message = "canal suportado: BROWSER") String> channels) {
}
