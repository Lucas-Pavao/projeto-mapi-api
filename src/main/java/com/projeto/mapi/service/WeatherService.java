package com.projeto.mapi.service;

import com.projeto.mapi.dto.WeatherResponseDTO;

public interface WeatherService {
    WeatherResponseDTO getWeatherData(double latitude, double longitude);
}
