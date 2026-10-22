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
public class SensorResponseDTO {
    private Long id;
    private String sensorId;
    private Double value;
    private String unit;
    private String batteryStatus;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
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

    // Estações fluviométricas da APAC com alerta oficial (ex.: Rio Duas Unas)
    private String riverName;
    private Double riverPreAlertLevel;
    private Double riverAlertLevel;
    private Double riverFloodLevel;

    // Proveniência (ADR 0006, aditivo): run do pipeline que produziu o valor vigente e a revisão.
    // Nulos enquanto os sensores vierem do legado sensor_data (ANA/APAC/Cemaden ainda não migrados).
    private String provenanceId;
    private Integer revision;

    // Frescor (redesign, GET /api/sensors/latest): preenchidos só nessa rota; null nas demais.
    // observedAt = timestamp em UTC; stale = ageSeconds > 2 x intervalo esperado da fonte.
    private java.time.Instant observedAt;
    private Long ageSeconds;
    private Boolean stale;
}
