package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloodPredictionRequestDTO {
    @JsonProperty("station_id")
    private String stationId;
    
    private Double latitude;
    private Double longitude;
    
    @JsonProperty("current_rainfall")
    private Double currentRainfall;
    
    @JsonProperty("rainfall_3h_accumulated")
    private Double rainfall3hAccumulated;
    
    @JsonProperty("rainfall_6h_accumulated")
    private Double rainfall6hAccumulated;

    @JsonProperty("rainfall_12h_accumulated")
    private Double rainfall12hAccumulated;

    @JsonProperty("rainfall_24h_accumulated")
    private Double rainfall24hAccumulated;
    
    @JsonProperty("tide_level")
    private Double tideLevel;
    
    @JsonProperty("river_level")
    private Double riverLevel;

    // Variáveis meteorológicas e de onda exigidas pela IA na inferência. Ausente = null: a IA responde
    // 422 com missing_features (ADR 0007 §3); o backend não imputa estes campos.
    @JsonProperty("wind_speed")
    private Double windSpeed;

    private Double temperature;

    @JsonProperty("apparent_temperature")
    private Double apparentTemperature;

    private Double humidity;

    private Double pressure;

    @JsonProperty("wave_height")
    private Double waveHeight;

    @JsonProperty("wave_period")
    private Double wavePeriod;

    @JsonProperty("wave_direction")
    private Double waveDirection;

    @JsonProperty("solar_radiation")
    private Double solarRadiation;

    @JsonProperty("nearby_sensors")
    private java.util.List<SensorReadingDTO> nearbySensors;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    // Contrato com a MAPI AI (Python/FastAPI): snake_case em TODOS os campos, de propósito —
    // diferente de MapiResponseDTO.SensorReadingDTO (mesma leitura de sensor, mas servida em
    // camelCase ao front). São dois DTOs deliberadamente separados porque cada lado da API tem
    // sua própria convenção de nomenclatura; MapiServiceImpl converte de um para o outro antes de
    // montar esta requisição.
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorReadingDTO {
        @JsonProperty("sensor_id")
        private String sensorId;

        private Double latitude;
        private Double longitude;
        private Double value;
        private String unit;
        private String type;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime timestamp;

        @JsonProperty("distance_km")
        private Double distanceKm;
    }
}

