package com.projeto.mapi.dto;

import com.projeto.mapi.model.FloodEvent.Severity;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class FloodEventDTO {
    private Long id;
    private String floodPointSlug;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Severity severity;
    private String description;
    private String confirmedBy;
}
