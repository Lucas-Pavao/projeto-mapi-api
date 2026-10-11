package com.projeto.mapi.service.export.label;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;

/**
 * Regras de rótulo da MODELAGEM-IA (A.1.4 e A.3.1), puras e sem I/O. {@code t} é o fim da hora (UTC), como em
 * observation_hourly_v. As ocorrências são as já casadas com UM ponto numa linkage_config.
 *
 * <ul>
 *   <li>{@code label_h<H>}: 1 se há ocorrência HOUR com instante em (t, t+H]; 0 se não; nulo (excluído) se a janela
 *       (t, t+H] encosta num intervalo DAY do ponto (DAY_EVENT_SAME_DAY) ou se há ocorrência em (t-G, t]
 *       (POST_EVENT_WINDOW; DAY conta se o intervalo intersecta a janela). DAY tem precedência.</li>
 *   <li>{@code label_day}: 1 se alguma ocorrência (HOUR ou DAY) toca o dia civil America/Recife de t; nunca excluído.</li>
 * </ul>
 * O rótulo olha para frente por definição (é o alvo); nunca deve entrar como feature.
 */
public final class OccurrenceLabeler {

    public static final ZoneId ZONE = ZoneId.of("America/Recife");
    public static final String DAY_EVENT_SAME_DAY = "DAY_EVENT_SAME_DAY";
    public static final String POST_EVENT_WINDOW = "POST_EVENT_WINDOW";

    /** Ocorrência casada: intervalo [from, to); {@code hour} = precisão HOUR (instante = from). */
    public record Occ(long id, Instant from, Instant to, boolean hour) {}

    /** Rótulo de uma hora. {@code byHorizon} tem valor nulo quando excluído. */
    public record Label(Map<Integer, Integer> byHorizon, int day, String excludedReason, String occurrenceIds) {}

    private OccurrenceLabeler() {}

    public static Label label(List<Occ> occs, Instant t, List<Integer> horizons, IntUnaryOperator postEventHours) {
        Map<Integer, Integer> byH = new LinkedHashMap<>();
        TreeSet<Long> ids = new TreeSet<>();
        String reason = null;
        int maxH = horizons.stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int h : horizons) {
            Instant end = t.plus(h, ChronoUnit.HOURS);
            Instant gStart = t.minus(postEventHours.applyAsInt(h), ChronoUnit.HOURS);
            List<Long> dayHits = new ArrayList<>(), postHits = new ArrayList<>(), pos = new ArrayList<>();
            for (Occ o : occs) {
                if (!o.hour() && !o.from().isAfter(end) && o.to().isAfter(t)) dayHits.add(o.id());
                if (o.hour()) {
                    if (o.from().isAfter(gStart) && !o.from().isAfter(t)) postHits.add(o.id());
                    if (o.from().isAfter(t) && !o.from().isAfter(end)) pos.add(o.id());
                } else if (!o.from().isAfter(t) && o.to().isAfter(gStart)) {
                    postHits.add(o.id());
                }
            }
            String why = !dayHits.isEmpty() ? DAY_EVENT_SAME_DAY : !postHits.isEmpty() ? POST_EVENT_WINDOW : null;
            if (why != null) {
                byH.put(h, null);
                ids.addAll(why.equals(DAY_EVENT_SAME_DAY) ? dayHits : postHits);
                if (h == maxH) reason = why;
            } else if (!pos.isEmpty()) {
                byH.put(h, 1);
                ids.addAll(pos);
            } else {
                byH.put(h, 0);
            }
        }
        LocalDate d = t.atZone(ZONE).toLocalDate();
        Instant dayStart = d.atStartOfDay(ZONE).toInstant(), dayEnd = d.plusDays(1).atStartOfDay(ZONE).toInstant();
        int day = 0;
        for (Occ o : occs) {
            if (o.from().isBefore(dayEnd) && o.to().isAfter(dayStart)) { day = 1; ids.add(o.id()); }
        }
        return new Label(byH, day, reason, String.join(";", ids.stream().map(String::valueOf).toList()));
    }
}
