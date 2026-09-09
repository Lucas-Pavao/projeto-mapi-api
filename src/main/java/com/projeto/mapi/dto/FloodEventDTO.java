package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.projeto.mapi.model.FloodEvent.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "O slug do ponto de alagamento é obrigatório")
    private String floodPointSlug;

    @NotNull(message = "O horário de início do alagamento é obrigatório")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
    private Severity severity;
    private String description;
    private String confirmedBy;
}
