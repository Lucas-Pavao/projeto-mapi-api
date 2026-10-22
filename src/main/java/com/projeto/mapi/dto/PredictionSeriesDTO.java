package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;

public record PredictionSeriesDTO(String slug, Instant from, Instant to, List<PredictionSummaryDTO> items) {
}
