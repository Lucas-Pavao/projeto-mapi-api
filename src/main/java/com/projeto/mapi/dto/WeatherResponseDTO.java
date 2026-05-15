package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WeatherResponseDTO(
    double latitude,
    double longitude,
    @JsonProperty("generationtime_ms") double generationTimeMs,
    double elevation,
    @JsonProperty("current") CurrentWeatherDTO current
) {}
