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
    public static class PreciseData {
        private String source; // "SENSOR", "OPEN_METEO" ou "MIXED"
        private LocalDateTime timestamp;
        
        // Dados Consolidados
        private Double precipitation;
        private Double temperature;
        private Double humidity;
        private Double pressure;
        private Double windSpeed;
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

        @Builder.Default
        private String message = "Dados consolidados baseados na melhor fonte disponível.";
    }
}
