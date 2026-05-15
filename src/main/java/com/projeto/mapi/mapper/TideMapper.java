package com.projeto.mapi.mapper;

import com.projeto.mapi.dto.*;
import com.projeto.mapi.model.*;
import java.util.stream.Collectors;
import java.util.Collections;

public class TideMapper {
    public static TideTableResponseDTO toDTO(TideTable entity) {
        if (entity == null) return null;
        return TideTableResponseDTO.builder()
                .id(entity.getId())
                .year(entity.getYear())
                .harborName(entity.getHarborName())
                .state(entity.getState())
                .timezone(entity.getTimezone())
                .card(entity.getCard())
                .dataCollectionInstitution(entity.getDataCollectionInstitution())
                .meanLevel(entity.getMeanLevel())
                .geoLocations(entity.getGeoLocations() != null ? 
                        entity.getGeoLocations().stream().map(TideMapper::toDTO).collect(Collectors.toList()) : Collections.emptyList())
                .months(entity.getMonths() != null ? 
                        entity.getMonths().stream().map(TideMapper::toDTO).collect(Collectors.toList()) : Collections.emptyList())
                .build();
    }

    public static GeoLocationDTO toDTO(GeoLocation entity) {
        if (entity == null) return null;
        return GeoLocationDTO.builder()
                .lat(entity.getLat())
                .lng(entity.getLng())
                .decimalLat(entity.getDecimalLat())
                .decimalLng(entity.getDecimalLng())
                .latDirection(entity.getLatDirection())
                .lngDirection(entity.getLngDirection())
                .build();
    }

    public static MonthDataDTO toDTO(MonthData entity) {
        if (entity == null) return null;
        return MonthDataDTO.builder()
                .month(entity.getMonth())
                .monthName(entity.getMonthName())
                .days(entity.getDays() != null ? 
                        entity.getDays().stream().map(TideMapper::toDTO).collect(Collectors.toList()) : Collections.emptyList())
                .build();
    }

    public static DayDataDTO toDTO(DayData entity) {
        if (entity == null) return null;
        return DayDataDTO.builder()
                .day(entity.getDay())
                .weekdayName(entity.getWeekdayName())
                .hours(entity.getHours() != null ? 
                        entity.getHours().stream().map(TideMapper::toDTO).collect(Collectors.toList()) : Collections.emptyList())
                .build();
    }

    public static HourDataDTO toDTO(HourData entity) {
        if (entity == null) return null;
        return HourDataDTO.builder()
                .hour(entity.getHour())
                .level(entity.getLevel())
                .build();
    }
}
