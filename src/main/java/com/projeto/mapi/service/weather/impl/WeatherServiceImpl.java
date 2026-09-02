package com.projeto.mapi.service.weather.impl;

import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.model.WeatherData;
import com.projeto.mapi.repository.WeatherDataRepository;
import com.projeto.mapi.service.weather.WeatherService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
    private final java.util.concurrent.Executor taskExecutor;

    public WeatherServiceImpl(RestClient.Builder restClientBuilder,
                             com.projeto.mapi.config.AppProperties appProperties,
                             WeatherDataRepository weatherDataRepository,
                             @org.springframework.beans.factory.annotation.Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor) {
        this.restClient = restClientBuilder
                .baseUrl(appProperties.getWeather().getApiUrl())
                .build();
        this.weatherDataRepository = weatherDataRepository;
        this.taskExecutor = taskExecutor;
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "weatherData", key = "T(java.lang.Math).round(#latitude * 100) / 100.0 + '-' + T(java.lang.Math).round(#longitude * 100) / 100.0")
    @Retry(name = "openMeteo")
    @CircuitBreaker(name = "openMeteo", fallbackMethod = "getWeatherDataFallback")
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
            // Usa o pool de threads dedicado da aplicação (taskExecutor) em vez do
            // ForkJoinPool.commonPool() padrão do CompletableFuture, evitando disputar
            // threads com outras tarefas paralelas da JVM para uma operação de I/O bloqueante (JPA save).
            java.util.concurrent.CompletableFuture.runAsync(() -> saveWeatherData(response), taskExecutor);
        }

        return response;
    }

    // Open-Meteo indisponível/instável: devolve um payload com current=null em vez de propagar a
    // exceção. WeatherController/MapiServiceImpl já lidam com clima ausente (é opcional na predição).
    private WeatherResponseDTO getWeatherDataFallback(double latitude, double longitude, Throwable t) {
        log.warn("Open-Meteo Weather indisponível para {},{}: {} — {}", latitude, longitude, t.getClass().getSimpleName(), t.getMessage());
        return new WeatherResponseDTO(latitude, longitude, 0.0, 0.0, null);
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
