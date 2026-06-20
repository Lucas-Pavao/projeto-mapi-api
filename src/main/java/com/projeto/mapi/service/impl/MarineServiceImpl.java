package com.projeto.mapi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.service.MarineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class MarineServiceImpl implements MarineService {

    private final RestClient restClient;

    public MarineServiceImpl(RestClient.Builder restClientBuilder, AppProperties appProperties) {
        this.restClient = restClientBuilder
                .baseUrl(appProperties.getMarine().getApiUrl())
                .build();
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "marineData", key = "T(java.lang.Math).round(#latitude * 100) / 100.0 + '-' + T(java.lang.Math).round(#longitude * 100) / 100.0")
    public JsonNode getMarineData(double latitude, double longitude) {
        return this.restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("hourly", "wave_height,wave_direction,wave_period")
                        .queryParam("timezone", "auto")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    @Override
    public Double getCurrentWaveHeight(double latitude, double longitude) {
        return getHourlyValue(latitude, longitude, "wave_height");
    }

    @Override
    public Double getCurrentWaveDirection(double latitude, double longitude) {
        return getHourlyValue(latitude, longitude, "wave_direction");
    }

    @Override
    public Double getCurrentWavePeriod(double latitude, double longitude) {
        return getHourlyValue(latitude, longitude, "wave_period");
    }

    private Double getHourlyValue(double latitude, double longitude, String field) {
        try {
            JsonNode data = getMarineData(latitude, longitude);
            if (data != null && data.has("hourly")) {
                JsonNode hourly = data.get("hourly");
                if (hourly.has("time") && hourly.has(field)) {
                    String nowStr = java.time.OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS).toString();
                    JsonNode times = hourly.get("time");
                    JsonNode values = hourly.get(field);
                    
                    for (int i = 0; i < times.size(); i++) {
                        if (times.get(i).asText().startsWith(nowStr.substring(0, 13))) {
                            return values.get(i).asDouble();
                        }
                    }
                    // Fallback para a hora atual se não encontrar por timestamp exato
                    int hour = java.time.LocalDateTime.now().getHour();
                    if (values.isArray() && values.size() > hour) {
                        return values.get(hour).asDouble();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro ao buscar {} via Open-Meteo Marine: {}", field, e.getMessage());
        }
        return null;
    }
}
