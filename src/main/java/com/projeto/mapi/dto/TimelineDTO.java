package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;

/** Linha do tempo agregada (contrato Timeline). Passo de 10 min. */
public record TimelineDTO(
        Instant from,
        Instant to,
        int stepMinutes,
        List<StationSeries> rain,
        TideSeries tide,
        List<StationSeries> river,
        List<RiskSeries> risk,
        List<TimelineFloodEvent> floodEvents) {

    public record TimePoint(Instant t, Double v) {
    }

    public record StationSeries(String stationId, List<TimePoint> points) {
    }

    public record TideSeries(String harbor, List<TimePoint> points) {
    }

    public record RiskSeries(String slug, List<PredictionSummaryDTO> items) {
    }

    public record TimelineFloodEvent(Long id, String slug, Instant startTime, String severity) {
    }
}
