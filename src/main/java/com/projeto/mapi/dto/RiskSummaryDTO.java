package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Contagem de pontos ativos por nível de risco da última previsão de cada um. */
public record RiskSummaryDTO(
        @JsonProperty("LOW") int low,
        @JsonProperty("MEDIUM") int medium,
        @JsonProperty("HIGH") int high,
        @JsonProperty("UNKNOWN") int unknown,
        Instant computedAt) {
}
