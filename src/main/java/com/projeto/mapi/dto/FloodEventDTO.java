package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class FloodEventDTO {
    private Long id;
    private String floodPointSlug;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
    private Severity severity;
    private String description;
    private String confirmedBy;
}
