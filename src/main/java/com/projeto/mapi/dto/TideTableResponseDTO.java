package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TideTableResponseDTO {
    private Long id;
    private Integer year;
    private String harborName;
    private String state;
    private String timezone;
    private String card;
    private String dataCollectionInstitution;
    private Float meanLevel;
    private List<GeoLocationDTO> geoLocations;
    private List<MonthDataDTO> months;
    private Double currentTideHeight;
    @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"OFFICIAL", "HARMONIC"}, nullable = true,
            description = "Fonte da maré do período: HARMONIC se algum extremo for da reserva harmônica; OFFICIAL se todos "
                    + "forem da tábua oficial; null sem extremos")
    private String tideSource;
}
