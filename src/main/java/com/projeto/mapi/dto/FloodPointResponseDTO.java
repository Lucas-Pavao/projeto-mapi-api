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
    private String slug;
    private String name;
    private String municipality;
    private String description;
    private Double latitude;
    private Double longitude;
    private Double altitudeM;
    private Double distanceToChannelM;
    private String basinName;
    private FloodPointRequestDTO.SensorConfigDTO sensorConfig;
    private java.util.List<String> nearbySensorIds;
    private MapiResponseDTO.PreciseData liveData;
    private FloodPredictionResponseDTO floodPrediction;
    private Boolean active;
    private Double tideHeight;
    private String tideUnit;
}
