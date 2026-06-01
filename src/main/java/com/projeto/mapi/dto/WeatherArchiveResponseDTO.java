package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WeatherArchiveResponseDTO(
    double latitude,
    double longitude,
    double elevation,
    @JsonProperty("hourly_units") HourlyUnitsDTO hourlyUnits,
    @JsonProperty("hourly") HourlyDataArraysDTO hourly
) {
    public record HourlyUnitsDTO(
        String time,
        String precipitation,
        @JsonProperty("temperature_2m") String temperature
    ) {}

    public record HourlyDataArraysDTO(
        List<String> time,
        List<Double> precipitation,
        @JsonProperty("temperature_2m") List<Double> temperature,
        @JsonProperty("relative_humidity_2m") List<Integer> humidity,
        @JsonProperty("weather_code") List<Integer> weatherCode
    ) {}
}
