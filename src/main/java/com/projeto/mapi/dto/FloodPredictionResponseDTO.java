package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloodPredictionResponseDTO {
    @JsonProperty("flood_probability")
    private Double floodProbability;
    
    @JsonProperty("risk_level")
    private String riskLevel; // "LOW", "MEDIUM", "HIGH", "EXTREME"
    
    @JsonProperty("estimated_time_to_event")
    private String estimatedTimeToEvent;
    
    private String message;
}

