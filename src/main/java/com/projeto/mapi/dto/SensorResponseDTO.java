package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorResponseDTO {
    private Long id;
    private String sensorId;
    private Double value;
    private String unit;
    private String batteryStatus;
    private LocalDateTime timestamp;
}
