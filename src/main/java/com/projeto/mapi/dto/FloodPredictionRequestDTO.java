package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class FloodPredictionRequestDTO {
    @JsonProperty("station_id")
    private String stationId;
    
    private Double latitude;
    private Double longitude;
    
    @JsonProperty("current_rainfall")
    private Double currentRainfall;
    
    @JsonProperty("rainfall_3h_accumulated")
    private Double rainfall3hAccumulated;
    
    @JsonProperty("rainfall_6h_accumulated")
    private Double rainfall6hAccumulated;

    @JsonProperty("rainfall_12h_accumulated")
    private Double rainfall12hAccumulated;

    @JsonProperty("rainfall_24h_accumulated")
    private Double rainfall24hAccumulated;
    
    @JsonProperty("tide_level")
    private Double tideLevel;
    
    @JsonProperty("river_level")
    private Double riverLevel;

    @JsonProperty("nearby_sensors")
    private java.util.List<MapiResponseDTO.SensorReadingDTO> nearbySensors;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}

