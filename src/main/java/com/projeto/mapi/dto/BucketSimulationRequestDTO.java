package com.projeto.mapi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/model/simulate/bucket. {@code f} e {@code curveScale} fora da faixa de sensibilidade registrada
 * (docs/PARAMETROS-MODELO-FISICO.md:66,69) devolvem 422 pelo serviço; a forma da chuva é validada aqui (400).
 */
public record BucketSimulationRequestDTO(
        @NotBlank(message = "slug é obrigatório") String slug,
        @NotNull(message = "rainfallMmH é obrigatório")
        @Size(min = 1, max = 72, message = "rainfallMmH deve ter de 1 a 72 valores")
        List<@NotNull @DecimalMin("0") @DecimalMax("300") Double> rainfallMmH,
        Double f,
        Double curveScale) {
}
