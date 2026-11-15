package com.projeto.mapi.service.station;

import com.projeto.mapi.dto.StationHourlyDTO;

import java.time.Instant;

/** Série horária por estação canônica com a fonte usada e o pico isolado marcado (ADR 0023). */
public interface StationHourlyService {

    int MAX_DAYS = 7;

    /** Horas com fim em (from, to]; lança InvalidRequestException se a janela passa de 7 dias e ResourceNotFoundException se a canônica não existe. */
    StationHourlyDTO hourly(String canonicalId, Instant from, Instant to);
}
