package com.projeto.mapi.dto;

import jakarta.validation.constraints.NotNull;

public record FloodScenarioLabelRequestDTO(
    @NotNull(message = "Latitude é obrigatória") Double latitude,
    @NotNull(message = "Longitude é obrigatória") Double longitude,
    @NotNull(message = "Status de alagamento é obrigatório") Boolean isFlooded
) {}
