package com.projeto.mapi.service.flood.impl;

import com.projeto.mapi.repository.analise.RainForecastCheckRepository.SeriesBreak;

import java.time.Instant;
import java.util.List;

/** Acesso de teste aos cortes por quebra de série (rótulo de cada trecho). */
public final class RainForecastCheckServiceImplAccess {
    private RainForecastCheckServiceImplAccess() {}

    public static List<String> segments(Instant from, Instant to, List<SeriesBreak> breaks) {
        return RainForecastCheckServiceImpl.segments(from, to, breaks).stream().map(RainForecastCheckServiceImpl.Segment::label).toList();
    }
}
