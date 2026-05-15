package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocationDTO {
    private String lat;
    private String lng;
    private String decimalLat;
    private String decimalLng;
    private String latDirection;
    private String lngDirection;
}
