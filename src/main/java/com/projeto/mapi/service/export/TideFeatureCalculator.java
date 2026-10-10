package com.projeto.mapi.service.export;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
     * Índice dos extremos para consulta por hora sem varrer a lista inteira: busca binária da próxima preamar e
     * amplitude por dia civil pré-agregada. Construir uma vez por chamada de export e reutilizar em cada {@code at}.
     */
    public static final class Index {
        private final Instant[] highTs;
        private final Map<LocalDate, double[]> dayStats; // dia -> {mín, máx, contagem}

        private Index(List<Extreme> extremes) {
            List<Extreme> sorted = new ArrayList<>(extremes);
            sorted.sort(Comparator.comparing(Extreme::ts)); // estável: ordem cronológica já declarada
            this.highTs = sorted.stream().filter(e -> "PREAMAR".equals(e.kind())).map(Extreme::ts)
                    .toArray(Instant[]::new);
            this.dayStats = new HashMap<>();
            for (Extreme e : sorted) {
                double[] st = dayStats.computeIfAbsent(e.ts().atZone(RECIFE).toLocalDate(),
                        d -> new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0});
                st[0] = Math.min(st[0], e.level());
                st[1] = Math.max(st[1], e.level());
                st[2]++;
            }
        }

    }

    public static Index index(List<Extreme> extremes) {
        return new Index(extremes);
    }

    /** Versão de conveniência (testes, uso pontual); no export use {@link #index} uma vez e a sobrecarga com {@link Index}. */
    public static Features at(Instant t, Map<Instant, Double> hourly, List<Extreme> extremes) {
        return at(t, hourly, index(extremes));
    }

    /**
     * @param hourly altura nas horas cheias UTC (chave = instante)
     * @param index  extremos em ordem cronológica (preamar/baixa-mar), indexados por {@link #index}
     */
    public static Features at(Instant t, Map<Instant, Double> hourly, Index index) {
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

        return new Features(h0 == null ? null : round2(h0), ahead, maxNext, hoursToNextHigh(t, index),
                rangeOfLocalDay(t, index), rising(slope, h0, next), slope);
    }

    /**
     * Sinal da maré em t: 1 se a derivada central {@code (h(t+1)-h(t-1))/2} é positiva, 0 se negativa. Derivada nula
     * (hora de preamar/baixa-mar, em que os vizinhos têm a mesma altura) desempata pelo passo à frente
     * {@code h(t+1)-h(t)}: positivo = 1, negativo = 0 (a maré prevista pode olhar à frente). Se também nulo, ou se
     * faltar insumo, fica nulo e o CSV marca {@code tide_rising_missing = 1}.
     */
    static Integer rising(Double slope, Double h0, Double next) {
        if (slope == null) return null;
        if (slope > 0) return 1;
        if (slope < 0) return 0;
        if (h0 == null || next == null) return null;
        double fwd = next - h0;
        if (fwd > 0) return 1;
        if (fwd < 0) return 0;
        return null;
    }

    static Double hoursToNextHigh(Instant t, Index index) {
        // primeira preamar com ts > t (busca binária; equivale à varredura linear em lista cronológica)
        Instant[] h = index.highTs;
        int lo = 0, hi = h.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (h[mid].isAfter(t)) hi = mid; else lo = mid + 1;
        }
        if (lo == h.length) return null;
        Duration d = Duration.between(t, h[lo]);
        return d.compareTo(MAX_TO_NEXT_HIGH) > 0 ? null : round2(d.toSeconds() / 3600.0);
    }

    /** Máximo menos mínimo dos extremos do dia civil (America/Recife) de t; precisa de pelo menos 2 extremos. */
    static Double rangeOfLocalDay(Instant t, Index index) {
        double[] st = index.dayStats.get(t.atZone(RECIFE).toLocalDate());
        return st == null || st[2] < 2 ? null : round2(st[1] - st[0]);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
