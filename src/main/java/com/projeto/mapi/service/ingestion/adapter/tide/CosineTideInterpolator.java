package com.projeto.mapi.service.ingestion.adapter.tide;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Interpolação por cosseno entre extremos consecutivos da tábua (padrão para tábuas de 3 a 4 extremos por dia):
 * {@code h(t) = h0 + (h1 - h0) * (1 - cos(pi * (t - t0) / (t1 - t0))) / 2}. Puro e determinístico.
 *
 * <p>É exata para uma maré senoidal de período 2*(t1-t0) e tem derivada nula nos extremos; a maré real (várias
 * componentes: o PDF cita 15 ou 16) desvia dela. A incerteza do método NÃO foi medida contra marégrafo
 * ({@code [sem fonte]}, ver docs/revisao/fase-2/F2-BACK-14.md). Não extrapola: horas antes do primeiro ou depois do
 * último extremo, ou em intervalo entre extremos acima de {@code maxGap}, ficam sem valor (lacuna, nunca constante).
 */
public final class CosineTideInterpolator {

    /** Extremo em instante UTC (a hora local da tábua já convertida pela validação). */
    public record Point(Instant ts, double level, String source) {}

    /** Valor interpolado de uma hora e os dois extremos que o geraram. */
    public record Hourly(Instant hour, double level, Point before, Point after) {}

    private CosineTideInterpolator() {}

    /** Valor em {@code t} entre {@code a} e {@code b} ({@code a.ts <= t <= b.ts}). */
    public static double between(Point a, Point b, Instant t) {
        long span = Duration.between(a.ts(), b.ts()).toMillis();
        if (span <= 0) return a.level();
        double x = (double) Duration.between(a.ts(), t).toMillis() / span;
        return a.level() + (b.level() - a.level()) * (1.0 - Math.cos(Math.PI * x)) / 2.0;
    }

    /** Horas cheias em {@code [from, to)} com extremo antes e depois (inclusive coincidente) a no máximo {@code maxGap}. */
    public static List<Hourly> hourly(List<Point> sorted, Instant from, Instant to, Duration maxGap) {
        List<Hourly> out = new ArrayList<>();
        if (sorted.size() < 2) return out;
        Instant h = ceilHour(from);
        int i = 0; // sorted[i] <= h < sorted[i+1] (ou h coincide com sorted[i])
        for (; h.isBefore(to); h = h.plus(Duration.ofHours(1))) {
            while (i + 1 < sorted.size() && !sorted.get(i + 1).ts().isAfter(h)) i++;
            Point a = sorted.get(i);
            if (a.ts().isAfter(h)) continue; // antes do primeiro extremo
            if (a.ts().equals(h)) {
                out.add(new Hourly(h, a.level(), a, a));
                continue;
            }
            if (i + 1 >= sorted.size()) break; // depois do último extremo
            Point b = sorted.get(i + 1);
            if (Duration.between(a.ts(), b.ts()).compareTo(maxGap) > 0) continue; // lacuna entre extremos
            out.add(new Hourly(h, between(a, b, h), a, b));
        }
        return out;
    }

    static Instant ceilHour(Instant t) {
        long s = t.getEpochSecond();
        long rem = Math.floorMod(s, 3600);
        if (rem == 0 && t.getNano() == 0) return t;
        return Instant.ofEpochSecond(s - rem + 3600);
    }
}
