package com.projeto.mapi.dto;

import com.projeto.mapi.model.FloodEvent.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScraperEventDTO {
    private Double latitude;
    private Double longitude;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Severity severity;
    private String description;
    private String source;
}
