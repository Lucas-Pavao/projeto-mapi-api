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
