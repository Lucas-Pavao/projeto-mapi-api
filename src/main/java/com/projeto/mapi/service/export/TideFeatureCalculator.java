package com.projeto.mapi.service.export;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Features de maré prevista por hora {@code t} (MODELAGEM-IA A.2.2): altura em t e em t+1..t+6, máximo em
 * (t, t+6], tempo até a próxima preamar, amplitude do dia local e sinal/valor da derivada. A maré prevista é a exceção
 * declarada de "futuro", porque a tábua é publicada antes. Pura; sem imputação: qualquer insumo ausente deixa a
 * feature nula. Alturas em m acima do NR da DHN ({@code NR_DHN_pe02}).
 */
public final class TideFeatureCalculator {

    public static final int HORIZON_HOURS = 6;
    /** Fuso civil só para o "dia" da amplitude; a série em si é UTC. */
    private static final ZoneId RECIFE = ZoneId.of("America/Recife");
    private static final Duration MAX_TO_NEXT_HIGH = Duration.ofHours(24);

    public record Extreme(Instant ts, double level, String kind) {}

    public record Features(Double height, Map<Integer, Double> ahead, Double maxNext6h, Double hoursToNextHigh,
                           Double rangeDayM, Integer rising, Double slopeMh) {}

    private TideFeatureCalculator() {}

    /**
     * @param hourly   altura nas horas cheias UTC (chave = instante)
     * @param extremes extremos em ordem cronológica (preamar/baixa-mar)
     */
    public static Features at(Instant t, Map<Instant, Double> hourly, List<Extreme> extremes) {
        Double h0 = hourly.get(t);
        Map<Integer, Double> ahead = new LinkedHashMap<>();
        Double max = null;
        boolean complete = true;
        for (int k = 1; k <= HORIZON_HOURS; k++) {
            Double v = hourly.get(t.plus(Duration.ofHours(k)));
            ahead.put(k, v == null ? null : round2(v));
            if (v == null) complete = false;
            else max = max == null ? v : Math.max(max, v);
        }
        Double maxNext = complete ? round2(max) : null;

        Double prev = hourly.get(t.minus(Duration.ofHours(1)));
        Double next = hourly.get(t.plus(Duration.ofHours(1)));
        Double slope = prev == null || next == null ? null : round2((next - prev) / 2.0);
        Integer rising = slope == null || slope == 0.0 ? null : (slope > 0 ? 1 : 0);

        return new Features(h0 == null ? null : round2(h0), ahead, maxNext, hoursToNextHigh(t, extremes),
                rangeOfLocalDay(t, extremes), rising, slope);
    }

    static Double hoursToNextHigh(Instant t, List<Extreme> extremes) {
        for (Extreme e : extremes) {
            if (e.ts().isAfter(t) && "PREAMAR".equals(e.kind())) {
                Duration d = Duration.between(t, e.ts());
                return d.compareTo(MAX_TO_NEXT_HIGH) > 0 ? null : round2(d.toSeconds() / 3600.0);
            }
        }
        return null;
    }

    /** Máximo menos mínimo dos extremos do dia civil (America/Recife) de t; precisa de pelo menos 2 extremos. */
    static Double rangeOfLocalDay(Instant t, List<Extreme> extremes) {
        LocalDate day = t.atZone(RECIFE).toLocalDate();
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int n = 0;
        for (Extreme e : extremes) {
            if (e.ts().atZone(RECIFE).toLocalDate().equals(day)) {
                min = Math.min(min, e.level());
                max = Math.max(max, e.level());
                n++;
            }
        }
        return n < 2 ? null : round2(max - min);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
