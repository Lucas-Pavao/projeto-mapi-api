package com.projeto.mapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloodPointRequestDTO {
    @NotBlank(message = "O nome do local é obrigatório")
    private String name;
    
    private String description;
    
    @NotNull(message = "A latitude é obrigatória")
    private Double latitude;
    
    @NotNull(message = "A longitude é obrigatória")
    private Double longitude;
    
    @NotNull(message = "A taxa de precipitação de alerta é obrigatória")
    private Double alertThresholdMm;
}
