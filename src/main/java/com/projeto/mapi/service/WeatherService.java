package com.projeto.mapi.service;

import com.projeto.mapi.dto.WeatherResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WeatherService {

    private final RestClient restClient;

    public WeatherService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.open-meteo.com/v1")
                .build();
    }

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
