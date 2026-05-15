package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CurrentWeatherDTO(
    String time,
    @JsonProperty("temperature_2m") double temperature,
    @JsonProperty("relative_humidity_2m") int humidity,
    @JsonProperty("apparent_temperature") double apparentTemperature,
    @JsonProperty("weather_code") int weatherCode,
    @JsonProperty("is_day") int isDay
) {}
