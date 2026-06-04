package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloodPredictionResponseDTO {
    private Double floodProbability;
    private String riskLevel; // "LOW", "MEDIUM", "HIGH", "EXTREME"
    private String estimatedTimeToEvent;
    private String message;
}
