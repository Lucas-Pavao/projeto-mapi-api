package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.model.WeatherData;
import com.projeto.mapi.repository.WeatherDataRepository;
import com.projeto.mapi.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class WeatherServiceImpl implements WeatherService {

    private final RestClient restClient;
    private final WeatherDataRepository weatherDataRepository;

    public WeatherServiceImpl(RestClient.Builder restClientBuilder, 
                             com.projeto.mapi.config.AppProperties appProperties,
                             WeatherDataRepository weatherDataRepository) {
        this.restClient = restClientBuilder
                .baseUrl(appProperties.getWeather().getApiUrl())
                .build();
        this.weatherDataRepository = weatherDataRepository;
    }

    @Override
    @Transactional
    public WeatherResponseDTO getWeatherData(double latitude, double longitude) {
        WeatherResponseDTO response = this.restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,surface_pressure,weather_code,is_day,precipitation,wind_speed_10m,shortwave_radiation")
                        .queryParam("timezone", "auto")
                        .build())
                .retrieve()
                .body(WeatherResponseDTO.class);

        if (response != null && response.current() != null) {
            saveWeatherData(response);
        }

        return response;
    }

    private void saveWeatherData(WeatherResponseDTO response) {
        try {
            var current = response.current();
            WeatherData data = WeatherData.builder()
                    .latitude(response.latitude())
                    .longitude(response.longitude())
                    .timestamp(LocalDateTime.parse(current.time(), DateTimeFormatter.ISO_DATE_TIME))
                    .temperature(current.temperature())
                    .apparentTemperature(current.apparentTemperature())
                    .humidity(current.humidity())
                    .pressure(current.surfacePressure())
                    .weatherCode(current.weatherCode())
                    .isDay(current.isDay() == 1)
                    .precipitation(current.precipitation())
                    .windSpeed(current.windSpeed())
                    .solarRadiation(current.solarRadiation())
                    .build();

            weatherDataRepository.save(data);
            log.info("Dados meteorológicos persistidos para {}, {}", response.latitude(), response.longitude());
        } catch (Exception e) {
            log.error("Erro ao persistir dados meteorológicos: {}", e.getMessage());
        }
    }
}
