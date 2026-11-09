package com.projeto.mapi.service.analise;

import com.projeto.mapi.dto.RainEventDTO;
import com.projeto.mapi.dto.RainEventSummaryDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Resumo por tipo (ADR 0017). Lógica pura. Eventos truncados não entram em nenhuma estatística (só na contagem
 * {@code truncatedExcluded}). Eventos com lacuna entram; {@code countWithoutGap} mostra quantos sobram sem eles.
 */
public final class RainEventSummarizer {

    /** Horas de um ano médio (365,25 dias). */
    public static final double HOURS_PER_YEAR = 8766.0;

    private RainEventSummarizer() {
    }

    /** Cobertura de um ponto: horas nominais da janela e horas com valor. */
    public record Coverage(long nominalHours, long coveredHours) {}

    public static List<RainEventSummaryDTO> summarize(String slug, List<RainEventDTO> events, Coverage cov) {
        List<RainEventDTO> mine = events.stream().filter(e -> slug.equals(e.floodPointSlug())).toList();
        List<RainEventSummaryDTO> out = new ArrayList<>();
        for (RainEventClassifier.Type t : RainEventClassifier.Type.values()) {
            out.add(row(slug, t.name(), mine, e -> t.name().equals(e.type()), cov));
        }
        out.add(row(slug, "TOTAL", mine, e -> true, cov));
        return out;
    }

    private static RainEventSummaryDTO row(String slug, String type, List<RainEventDTO> all,
                                           Predicate<RainEventDTO> ofType, Coverage cov) {
        List<RainEventDTO> typed = all.stream().filter(ofType).toList();
        List<RainEventDTO> used = typed.stream().filter(e -> !Boolean.TRUE.equals(e.truncated())).toList();
        int truncated = typed.size() - used.size();
        int n = used.size();
        int withoutGap = (int) used.stream().filter(e -> !Boolean.TRUE.equals(e.hasGap())).count();
        double nominalYears = cov.nominalHours() / HOURS_PER_YEAR;
        double coveredYears = cov.coveredHours() / HOURS_PER_YEAR;
        List<RainEventDTO> above = used.stream()
                .filter(e -> e.hoursAboveCapacity() != null && e.hoursAboveCapacity() > 0).toList();
        double eventHours = used.stream().mapToDouble(e -> e.durationMin() / 60.0).sum();
        return new RainEventSummaryDTO(slug, type, n, withoutGap, truncated,
                nominalYears > 0 ? round(n / nominalYears, 4) : null,
                coveredYears > 0 ? round(n / coveredYears, 4) : null,
                mean(used, e -> e.durationMin()),
                quantile(values(used, e -> e.durationMin()), 0.5),
                mean(used, RainEventDTO::meanIntensityMmH),
                quantile(values(used, RainEventDTO::meanIntensityMmH), 0.9),
                meanOfNullable(used.stream().map(RainEventDTO::dryHoursBefore).toList()),
                mean(used, RainEventDTO::totalMm),
                cov.coveredHours() > 0 ? round(eventHours / cov.coveredHours(), 6) : null,
                above.size(),
                above.isEmpty() ? null : mean(above, e -> e.hoursAboveCapacity()),
                cov.nominalHours() > 0 ? round((double) cov.coveredHours() / cov.nominalHours(), 6) : null);
    }

    private static double[] values(List<RainEventDTO> l, ToDoubleFunction<RainEventDTO> f) {
        return l.stream().mapToDouble(f).sorted().toArray();
    }

    private static Double mean(List<RainEventDTO> l, ToDoubleFunction<RainEventDTO> f) {
        return l.isEmpty() ? null : round(l.stream().mapToDouble(f).average().orElseThrow(), 4);
    }

    private static Double meanOfNullable(List<Double> l) {
        double[] v = l.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        return v.length == 0 ? null : round(Arrays.stream(v).average().orElseThrow(), 4);
    }

    /** Quantil por interpolação linear entre as estatísticas de ordem (tipo 7 de Hyndman e Fan). */
    static Double quantile(double[] sorted, double p) {
        if (sorted.length == 0) return null;
        double h = (sorted.length - 1) * p;
        int lo = (int) Math.floor(h);
        int hi = Math.min(lo + 1, sorted.length - 1);
        return round(sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo]), 4);
    }

    static Double round(double v, int scale) {
        return Math.round(v * Math.pow(10, scale)) / Math.pow(10, scale);
    }

    /** Ordem estável das linhas: por slug, depois FRACA, MEDIA, FORTE, TOTAL. */
    public static List<RainEventSummaryDTO> summarizeAll(List<String> slugs, List<RainEventDTO> events,
                                                         Map<String, Coverage> coverage) {
        List<RainEventSummaryDTO> out = new ArrayList<>();
        for (String s : slugs) out.addAll(summarize(s, events, coverage.getOrDefault(s, new Coverage(0, 0))));
        return out;
    }
}
