package com.projeto.mapi.mapper;

import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.PredictionSummaryDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PredictionSummaryMapper {

    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "probability", source = "prediction.floodProbability")
    PredictionSummaryDTO toSummary(FloodPredictionResponseDTO prediction, String slug);
}
