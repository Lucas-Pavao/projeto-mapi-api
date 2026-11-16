package com.projeto.mapi.service.weather.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.weather.OpenMeteoForecastClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpenMeteoForecastClientImpl implements OpenMeteoForecastClient {

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public OpenMeteoForecastClientImpl(
            @Value("${app.weather.previous-runs-url:https://previous-runs-api.open-meteo.com/v1}") String baseUrl,
            ExternalRestClientFactory http, ObjectMapper mapper) {
        this.restClient = http.builder("open-meteo").baseUrl(baseUrl).build();
        this.mapper = mapper;
    }

    @Override
    public Batch previousDayRun(double lat, double lon) {
        String raw = restClient.get().uri(u -> u.path("/forecast").queryParam("latitude", lat).queryParam("longitude", lon)
                        .queryParam("hourly", "precipitation_previous_day1").queryParam("past_days", 1)
                        .queryParam("forecast_days", 2).queryParam("timezone", "UTC").build())
                .retrieve().body(String.class);
        try {
            JsonNode root = mapper.readTree(raw);
            // Fuso declarado na resposta: só aceita UTC (ADR 0001).
            if (root.path("utc_offset_seconds").asInt(-1) != 0) {
                throw new IllegalStateException("Open-Meteo respondeu fora de UTC");
            }
            JsonNode time = root.path("hourly").path("time");
            JsonNode mm = root.path("hourly").path("precipitation_previous_day1");
            List<Hour> hours = new ArrayList<>();
            for (int i = 0; i < time.size(); i++) {
                // precipitação da hora que TERMINA no carimbo (soma da hora anterior)
                Instant end = LocalDateTime.parse(time.get(i).asText()).toInstant(ZoneOffset.UTC);
                hours.add(new Hour(end, mm.get(i) == null || mm.get(i).isNull() ? null : mm.get(i).asDouble()));
            }
            return new Batch(root.path("latitude").isNumber() ? root.path("latitude").asDouble() : null,
                    root.path("longitude").isNumber() ? root.path("longitude").asDouble() : null, Hashing.sha256(raw), hours);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Resposta do Open-Meteo ilegível: " + e.getMessage(), e);
        }
    }
}
