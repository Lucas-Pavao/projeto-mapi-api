package com.projeto.mapi.service.sensor;

import java.time.Instant;
import java.util.List;

/**
 * Chuva horária da série consolidada (ADR 0006): soma de {@code observation.precipitation} por hora, só registros
 * ACTIVE. É a fonte única de RAIN_RATE, de /api/public/risk e da chuva da linha do tempo (decisão 45).
 */
public interface HourlyRainService {

    /** Chuva de uma hora fechada de uma estação. {@code lat}/{@code lon} nulos se a estação não tem coordenada. */
    record StationRain(String source, String stationId, Instant hourEnd, double mm, Double lat, Double lon) {
    }

    /** Idade máxima do fim da hora para valer como "última hora" (a ingestão fecha a hora com atraso). */
    int MAX_AGE_HOURS = 3;

    /** Última hora fechada de cada estação, com fim nas últimas {@value #MAX_AGE_HOURS} h. */
    List<StationRain> latestClosedHour();

    /** O código da estação na observação (ex.: 261160...) casa com o sensorId do legado (APAC-PLUVIO-261160...). */
    static boolean sameStation(String observationStationId, String otherId) {
        if (observationStationId == null || otherId == null) return false;
        return strip(observationStationId).equalsIgnoreCase(strip(otherId));
    }

    private static String strip(String id) {
        return id.replaceFirst("^(APAC-PLUVIO-|APAC-METEO-|APAC-RIO-|ANA-)", "");
    }
}
