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
    @NotBlank(message = "O ID do ponto (slug) é obrigatório")
    private String slug;

    @NotBlank(message = "O nome do local é obrigatório")
    private String name;

    private String municipality;
    private String description;

    @NotNull(message = "A latitude é obrigatória")
    private Double latitude;

    @NotNull(message = "A longitude é obrigatória")
    private Double longitude;

    private Double altitudeM;
    private Double distanceToChannelM;

    private SensorConfigDTO sensorConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorConfigDTO {
        private java.util.List<String> pluviometerStationIds;
        private java.util.List<String> riverLevelStationIds;
    }
}
