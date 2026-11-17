package com.projeto.mapi.service.tide;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Origem da maré servida: tábua oficial (TabuaMare/DHN) ou a reserva harmônica (TIDE_HARMONIC_F41, V36).
 * Mapeia a coluna {@code source} de {@code observation}; não decide qual extremo vale (isso é da consulta).
 */
public enum TideSource {
    OFFICIAL, HARMONIC;

    private static final String HARMONIC_SOURCE = "TIDE_HARMONIC_F41";

    /** {@code TIDE_HARMONIC_F41} vira HARMONIC; qualquer outra fonte de maré (tábuas) é OFFICIAL; null fica null. */
    public static TideSource fromObservationSource(String source) {
        if (source == null) return null;
        return HARMONIC_SOURCE.equals(source) ? HARMONIC : OFFICIAL;
    }

    /** HARMONIC se algum for HARMONIC; senão OFFICIAL se algum existir; senão null. */
    public static TideSource combine(TideSource a, TideSource b) {
        if (a == HARMONIC || b == HARMONIC) return HARMONIC;
        return a != null ? a : b;
    }

    /**
     * Fonte do valor interpolado em {@code t}: combina os dois extremos que cercam o instante (o último em ou antes
     * e o primeiro depois); sem cerco, combina todos os extremos recebidos. Vazio dá null.
     */
    public static <T> TideSource at(List<T> extremes, Instant t, Function<T, Instant> ts, Function<T, String> source) {
        T before = null;
        T after = null;
        for (T e : extremes) {
            Instant i = ts.apply(e);
            if (!i.isAfter(t)) {
                if (before == null || i.isAfter(ts.apply(before))) before = e;
            } else if (after == null || i.isBefore(ts.apply(after))) {
                after = e;
            }
        }
        if (before != null && after != null) {
            return combine(fromObservationSource(source.apply(before)), fromObservationSource(source.apply(after)));
        }
        TideSource all = null;
        for (T e : extremes) all = combine(all, fromObservationSource(source.apply(e)));
        return all;
    }
}
