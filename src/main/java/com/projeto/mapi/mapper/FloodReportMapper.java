package com.projeto.mapi.mapper;

import com.projeto.mapi.dto.FloodReportDTO;
import com.projeto.mapi.model.FloodReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface FloodReportMapper {

    @Mapping(target = "nearestPointSlug", source = "nearestPoint.slug")
    FloodReportDTO toDto(FloodReport report);
}
