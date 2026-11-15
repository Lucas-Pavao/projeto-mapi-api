package com.projeto.mapi.service.analise;

import com.projeto.mapi.dto.StationMonthChecksDTO;

import java.time.Instant;

/** Grade estação x mês da checagem espacial (ADR 0022). */
public interface StationMonthChecksService {

    int MAX_MONTHS = 24;

    /** Meses com início em [from, to); nulos = últimos 6 meses. InvalidRequestException se a janela passa de 24 meses. */
    StationMonthChecksDTO grid(Instant from, Instant to);
}
