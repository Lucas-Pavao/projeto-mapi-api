package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloodPointResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Double latitude;
    private Double longitude;
    private Double alertThresholdMm;
    private Boolean active;
}
