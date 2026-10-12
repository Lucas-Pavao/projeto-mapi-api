package com.projeto.mapi.dto;

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
public class MapiResponseDTO {
    private double requestedLatitude;
    private double requestedLongitude;
    private PreciseData preciseData;
    private SensorResponseDTO nearestSensor;
    private WeatherResponseDTO openMeteoData;
    private Double distanceToNearestSensorKm;
    // Altitude do local: cota do MDT do PE3D (5 m) quando o ponto está na área levantada; senão, Open-Meteo (90 m).
    private LocalElevationDTO localElevation;
    private FloodPredictionResponseDTO floodPrediction;

    // Contrato de RESPOSTA ao front-end (camelCase, igual ao resto da MapiResponseDTO) — não
    // confundir com FloodPredictionRequestDTO.SensorReadingDTO, que é o mesmo dado só que no
    // formato snake_case exigido pelo contrato da MAPI AI (Python/FastAPI). Antes esta única
    // classe era reaproveitada nos dois sentidos via @JsonProperty forçando sensor_id/distance_km
    // aqui — o que vazava snake_case para o front no meio de um objeto camelCase.
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorReadingDTO {
        private String sensorId;
        private Double latitude;
        private Double longitude;
        private Double value;
        private String unit;
        private String type; // "PRECIPITATION", "RIVER_LEVEL", etc.

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime timestamp;

        private Double distanceKm;
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreciseData {
        private String source; // "SENSOR", "OPEN_METEO" ou "MIXED"

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime timestamp;
        private java.util.List<String> sensorIds;
        private java.util.List<SensorReadingDTO> latestReadings;
        private Aggregates historicalAggregates;
        
        // Dados Consolidados (Máximos/Médias Instantâneos)
        private Double precipitation;
        private Double temperature;
        private Double apparentTemperature;
        private Double humidity;
        private Double pressure;
        private Double windSpeed;
        private Double solarRadiation;
        private Double waterLevel;
        private Double flowRate;
        private Double tideHeight;
        private Double waveHeight;
        private Double waveDirection;
        private Double wavePeriod;
        
        private Double tideHeightTabuaMare;
        
        private String unitPrecipitation;
        private String unitTemperature;
        private String unitWaterLevel;
        private String unitTide;
        private String unitWave;
        private String unitPressure;
        private String unitWindSpeed;
        private String unitSolarRadiation;
        private String unitFlowRate;

        @Builder.Default
        private String message = "Dados consolidados baseados na melhor fonte disponível.";
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Aggregates {
        private Double rain3h;
        private Double rain6h;
        private Double rain12h;
        private Double rain24h;
        // Origem dos acumulados de chuva: OPEN_METEO_ARCHIVE (grade horária do pipeline) ou
        // OPEN_METEO_FORECAST (últimas 24 h do forecast, quando a grade não cobre a hora atual).
        private String rainSource;
        private Double maxRiverLevel24h;
        private Double avgTemperature24h;
    }
}
