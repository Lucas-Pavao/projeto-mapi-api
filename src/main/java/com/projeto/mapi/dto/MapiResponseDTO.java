package com.projeto.mapi.dto;

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
    private FloodPredictionResponseDTO floodPrediction;

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
        private LocalDateTime timestamp;
        private Double distanceKm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreciseData {
        private String source; // "SENSOR", "OPEN_METEO" ou "MIXED"
        private LocalDateTime timestamp;
        private java.util.List<String> sensorIds;
        private java.util.List<SensorReadingDTO> latestReadings;
        private Aggregates historicalAggregates;
        
        // Dados Consolidados (Máximos/Médias Instantâneos)
        private Double precipitation;
        private Double temperature;
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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Aggregates {
        private Double rain3h;
        private Double rain6h;
        private Double rain12h;
        private Double rain24h;
        private Double maxRiverLevel24h;
        private Double avgTemperature24h;
    }
}
