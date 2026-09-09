package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.LocalDateTime;

// @Builder aqui não é cosmético: o construtor posicional original tinha 21 parâmetros Double/String
// seguidos (windSpeed, temperature, apparentTemperature, humidity, pressure, waveHeight,
// wavePeriod...), e nada no compilador impede trocar a ordem de dois campos do mesmo tipo por
// engano na chamada. O builder nomeado em MapiServiceImpl.registerScenarioLabel elimina esse risco
// sem alterar o formato JSON serializado (Jackson usa os nomes dos accessors do record, não a
// posição do construtor).
@Builder
public record FloodScenarioLabelResponseDTO(
    Long id,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
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
