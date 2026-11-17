package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourDataDTO {
    private String hour;
    private Float level;
    @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"OFFICIAL", "HARMONIC"},
            description = "Fonte deste extremo: OFFICIAL (tábua oficial) ou HARMONIC (reserva harmônica)")
    private String source;
}
