package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;

/** Linha do tempo agregada (contrato Timeline). Passo de 10 min. O nível dos pontos vem de GET /api/alagamento/historico. */
public record TimelineDTO(
        Instant from,
        Instant to,
        int stepMinutes,
        List<StationSeries> rain,
        TideSeries tide,
        List<StationSeries> river,
        List<TimelineFloodEvent> floodEvents) {

    public record TimePoint(Instant t, Double v) {
    }

    public record StationSeries(String stationId, List<TimePoint> points) {
    }

    public record TideSeries(String harbor, List<TimePoint> points) {
    }

    public record TimelineFloodEvent(Long id, String slug, Instant startTime, String severity) {
    }
}
