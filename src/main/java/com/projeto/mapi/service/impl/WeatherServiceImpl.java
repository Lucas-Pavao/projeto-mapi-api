package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.service.WeatherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WeatherServiceImpl implements WeatherService {

    private final RestClient restClient;

    public WeatherServiceImpl(RestClient.Builder restClientBuilder, 
                             com.projeto.mapi.config.AppProperties appProperties) {
        this.restClient = restClientBuilder
                .baseUrl(appProperties.getWeather().getApiUrl())
                .build();
    }

    @Override
    public WeatherResponseDTO getWeatherData(double latitude, double longitude) {
        return this.restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day")
                        .queryParam("timezone", "auto")
                        .build())
                .retrieve()
                .body(WeatherResponseDTO.class);
    }
}
