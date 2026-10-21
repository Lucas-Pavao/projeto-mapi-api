package com.projeto.mapi.service.sensor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Intervalo esperado de cada fonte, lido da mesma configuração dos jobs agendados
 * ({@code SensorCollectionTask}). Um sensor está "sem resposta" (stale) quando a idade da última
 * leitura passa de 2 vezes esse intervalo (contrato SensorFreshness).
 */
@Component
public class SensorFreshnessPolicy {

    public static final int STALE_FACTOR = 2;

    private final long anaMs;
    private final long cemadenMs;
    private final long apacMetMs;

    public SensorFreshnessPolicy(@Value("${app.ana.fixed-rate-ms:900000}") long anaMs,
                                 @Value("${app.apac.cemaden-fixed-rate-ms:180000}") long cemadenMs,
                                 @Value("${app.apac.meteorologia-fixed-rate-ms:300000}") long apacMetMs) {
        this.anaMs = anaMs;
        this.cemadenMs = cemadenMs;
        this.apacMetMs = apacMetMs;
    }

    /** Nome canônico da fonte (CEMADEN, APAC, ANA) a partir de {@code source}/{@code sensorId}; null se desconhecida. */
    public static String canonicalSource(String source, String sensorId) {
        String s = source == null ? "" : source.toUpperCase(Locale.ROOT);
        String id = sensorId == null ? "" : sensorId.toUpperCase(Locale.ROOT);
        if (s.contains("CEMADEN")) return "CEMADEN";
        if (s.contains("ANA")) return "ANA";
        if (s.contains("APAC") || id.contains("APAC")) return "APAC";
        if (id.startsWith("ANA")) return "ANA";
        return s.isBlank() ? null : s;
    }

    /** Intervalo esperado em segundos; fonte desconhecida usa o da ANA (o mais longo). */
    public long expectedIntervalSeconds(String canonicalSource) {
        long ms = switch (canonicalSource == null ? "" : canonicalSource) {
            case "CEMADEN" -> cemadenMs;
            case "APAC" -> apacMetMs;
            default -> anaMs;
        };
        return ms / 1000;
    }

    public boolean isStale(String canonicalSource, long ageSeconds) {
        return ageSeconds > STALE_FACTOR * expectedIntervalSeconds(canonicalSource);
    }
}
