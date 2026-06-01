package com.projeto.mapi.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UnifiedDataDTO {
    private String floodPointSlug;
    private LocalDateTime timestamp;
    
    // Sensores Locais
    private Double sensorPrecipitation;
    private Double sensorWaterLevel;
    
    // Clima (Open-Meteo)
    private Double weatherPrecipitation;
    private Double weatherTemperature;
    private Integer weatherCode;
    
    // Maré
    private Double tideHeight;
    
    // Label (IA)
    private Boolean isFlooded;
    private String severity;
}
