package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UnifiedDataDTO {
    private String floodPointSlug;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * Mesmo instante de {@code timestamp}, em ISO-8601 com Z (ADR 0006). O eixo da exportação é UTC e o rótulo é o
     * FIM da hora (ADR 0006 seção 5); vale para os campos {@code weather*} (observation_hourly_v). Os campos
     * {@code sensor*} ainda vêm de sensor_data (hora local sem fuso, UNVERIFIED) e são alinhados a este eixo
     * pela hora "crua": o {@code Z} NÃO atesta o fuso deles. {@code timestamp} segue sem fuso por compatibilidade.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private java.time.Instant timestampUtc;
    
    // Sensores Locais
    private Double sensorPrecipitation;
    private Double sensorWaterLevel;
    private Double sensorSoilHumidity;
    
    // Clima (Open-Meteo)
    private Double weatherPrecipitation;
    private Double weatherTemperature;
    private Double weatherPressure;
    private Integer weatherCode;
    
    // Maré
    private Double tideHeight;
    
    // Label (IA)
    private Boolean isFlooded;
    private String severity;

    // Features Calculadas
    private Double accumulated3h;
    private Double accumulated6h;
    private Double accumulated12h;
    private Double accumulated24h;
    private Double accumulated48h;
}
