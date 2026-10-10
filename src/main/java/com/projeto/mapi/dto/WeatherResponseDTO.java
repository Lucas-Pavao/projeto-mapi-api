package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Clima atual (Open-Meteo current). {@code provenanceId}/{@code revision} (ADR 0006, aditivos) ficam nulos
 * enquanto este endpoint for proxy ao vivo, sem passar pelo pipeline (OPEN_METEO_CURRENT pendente).
 */
public record WeatherResponseDTO(
    double latitude,
    double longitude,
    @JsonProperty("generationtime_ms") double generationTimeMs,
    double elevation,
    @JsonProperty("current") CurrentWeatherDTO current,
    String provenanceId,
    Integer revision
) {
    public WeatherResponseDTO(double latitude, double longitude, double generationTimeMs, double elevation,
                              CurrentWeatherDTO current) {
        this(latitude, longitude, generationTimeMs, elevation, current, null, null);
    }
}
