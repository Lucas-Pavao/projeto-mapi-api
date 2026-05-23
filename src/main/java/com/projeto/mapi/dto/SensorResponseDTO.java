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
public class SensorResponseDTO {
    private Long id;
    private String sensorId;
    private Double value;
    private String unit;
    private String batteryStatus;
    private LocalDateTime timestamp;
    private String stationName;
    private Double latitude;
    private Double longitude;
    private String municipality;
    private String type;
    private String source;

    // Novos campos adaptados
    private Double fogValueReference;
    private String code;
    private Double temperature;
    private Double humidity;
    private Double pressure;
    private Double windSpeed;
    private String windDirection;
    private Double solarRadiation;
    private Double accumulatedPrecipitation;
    private Object soilHumidity;
    private Double waterLevel;
    private Double flowRate;
    private String basinName;
    private Double tideHeight;
}
