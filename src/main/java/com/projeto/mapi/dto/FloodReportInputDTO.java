package com.projeto.mapi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record FloodReportInputDTO(
        @NotNull(message = "latitude é obrigatória") @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull(message = "longitude é obrigatória") @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull(message = "observedAt é obrigatório") Instant observedAt,
        @Pattern(regexp = "ANKLE|KNEE|WAIST|ABOVE_WAIST", message = "waterDepth deve ser ANKLE, KNEE, WAIST ou ABOVE_WAIST") String waterDepth,
        @Size(max = 1000, message = "description aceita até 1000 caracteres") String description) {
}
