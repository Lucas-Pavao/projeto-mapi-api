package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Contrato de SAÍDA pro nosso front (camelCase, igual ao resto da API) — não confundir com o
// shape bruto que a MAPI AI devolve (snake_case, ver FloodPredictionServiceImpl.AiPredictionDTO).
// Antes esta mesma classe tinha @JsonProperty forçando flood_probability/risk_level/
// estimated_time_to_event, vazando o formato da IA direto pro front (que já tinha até um shim de
// normalização em flood.service.ts pra compensar isso).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloodPredictionResponseDTO {
    private Double floodProbability;
    private String riskLevel; // "LOW", "MEDIUM", "HIGH", "EXTREME"
    private String estimatedTimeToEvent;
    private String message;
}

