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
public class DayDataDTO {
    private String weekdayName;
    private Integer day;
    private List<HourDataDTO> hours;
}
