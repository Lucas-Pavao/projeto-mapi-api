package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CurrentWeatherDTO(
    String time,
    @JsonProperty("temperature_2m") double temperature,
    @JsonProperty("relative_humidity_2m") double humidity,
    @JsonProperty("apparent_temperature") double apparentTemperature,
    @JsonProperty("surface_pressure") Double surfacePressure,
    @JsonProperty("weather_code") int weatherCode,
    @JsonProperty("is_day") int isDay,
    @JsonProperty("precipitation") double precipitation
) {}
