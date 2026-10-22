package com.projeto.mapi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.util.List;

/**
 * PATCH /api/pontos/{slug}. Campo ausente/null = não muda. Para limpar {@code description}, envie "".
 * Latitude/longitude seguem a caixa da RMR do contrato.
 */
public record FloodPointPatchDTO(
        String name,
        String description,
        @DecimalMin("-8.4") @DecimalMax("-7.8") Double latitude,
        @DecimalMin("-35.2") @DecimalMax("-34.8") Double longitude,
        Boolean active,
        Double alertThresholdMm,
        List<String> pluviometerStationIds,
        List<String> riverLevelStationIds,
        List<String> weatherStationIds) {
}
