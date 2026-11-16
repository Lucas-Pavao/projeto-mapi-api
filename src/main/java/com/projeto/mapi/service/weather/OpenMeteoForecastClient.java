package com.projeto.mapi.service.weather;

import java.time.Instant;
import java.util.List;

/**
 * Previsão do Open-Meteo pela rodada da véspera (previous-runs-api, {@code precipitation_previous_day1}): cada hora
 * foi prevista por uma rodada emitida pelo menos 24 h antes. É previsão, não reanálise (fonte OPEN_METEO_FORECAST).
 */
public interface OpenMeteoForecastClient {

    record Hour(Instant hourEndUtc, Double mm) {}

    record Batch(Double gridLat, Double gridLon, String rawHash, List<Hour> hours) {}

    /** Ontem, hoje e amanhã, em UTC. Lança em falha de rede ou resposta sem fuso UTC. */
    Batch previousDayRun(double lat, double lon);
}
