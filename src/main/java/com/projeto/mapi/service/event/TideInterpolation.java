package com.projeto.mapi.service.event;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maré prevista a cada passo, por cosseno entre extremos consecutivos (mesmo método da série horária
 * {@code TIDE_PE02_HOURLY}). Não interpola se o intervalo entre extremos passa de {@link #MAX_GAP}
 * (o semiperíodo semidiurno é ~6,2 h; o limite é {@code [sem fonte]}, igual ao do adaptador horário).
 */
public final class TideInterpolation {

    public static final Duration MAX_GAP = Duration.ofHours(8);

    public record Extreme(Instant ts, double level) {
    }

    public record Sample(Instant t, Double v) {
    }

    private TideInterpolation() {
    }

    /** {@code extremes} ordenados por instante. Instantes fora de qualquer par de extremos ficam com v = null. */
    public static List<Sample> sample(List<Extreme> extremes, Instant from, Instant to, Duration step) {
        List<Sample> out = new ArrayList<>();
        int i = 0;
        for (Instant t = from; t.isBefore(to); t = t.plus(step)) {
            while (i + 1 < extremes.size() && !extremes.get(i + 1).ts().isAfter(t)) i++;
            out.add(new Sample(t, valueAt(extremes, i, t)));
        }
        return out;
    }

    private static Double valueAt(List<Extreme> ex, int i, Instant t) {
        if (ex.size() < 2 || i + 1 >= ex.size()) return null;
        Extreme a = ex.get(i), b = ex.get(i + 1);
        if (t.isBefore(a.ts()) || Duration.between(a.ts(), b.ts()).compareTo(MAX_GAP) > 0) return null;
        double frac = (double) Duration.between(a.ts(), t).toMillis() / Duration.between(a.ts(), b.ts()).toMillis();
        double v = a.level() + (b.level() - a.level()) * (1 - Math.cos(Math.PI * frac)) / 2.0;
        return Math.round(v * 100.0) / 100.0;
    }
}
