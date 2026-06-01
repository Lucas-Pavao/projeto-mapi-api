package com.projeto.mapi.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class DataHealthReportDTO {
    private String slug;
    private long totalWeatherRecords;
    private long totalSensorRecords;
    private long totalFloodEvents;
    private Map<Integer, Long> weatherRecordsByYear;
    private Map<Integer, Long> sensorRecordsByYear;
    private String status; // OK, INCOMPLETE, EMPTY
}
