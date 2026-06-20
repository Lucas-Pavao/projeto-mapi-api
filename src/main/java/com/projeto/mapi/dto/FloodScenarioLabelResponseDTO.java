package com.projeto.mapi.dto;

import java.time.LocalDateTime;

public record FloodScenarioLabelResponseDTO(
    Long id,
    LocalDateTime timestamp,
    Double latitude,
    Double longitude,
    Boolean isFlooded,
    Double currentRainfall,
    Double rainfall3hAccumulated,
    Double rainfall6hAccumulated,
    Double rainfall12hAccumulated,
    Double rainfall24hAccumulated,
    Double tideLevel,
    Double riverLevel,
    Double windSpeed,
    String windDirection,
    Double temperature,
    Double apparentTemperature,
    Double humidity,
    Double pressure,
    Double waveHeight,
    Double wavePeriod,
    Double waveDirection,
    Double solarRadiation
) {}
