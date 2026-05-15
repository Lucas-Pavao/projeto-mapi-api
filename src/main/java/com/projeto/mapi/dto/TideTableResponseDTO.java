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
}
