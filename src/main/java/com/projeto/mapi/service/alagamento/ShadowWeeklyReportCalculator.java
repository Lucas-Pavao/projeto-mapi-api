package com.projeto.mapi.service.alagamento;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Relatório semanal do modo sombra (onda 16, Fase 9.4), puro: sem banco nem rede. Por ponto, na janela [from, to):
 * ciclos feitos contra esperados (um a cada 10 min), maior buraco sem ciclo (contando as bordas da janela), buracos acima de
 * 30 min, ciclos por nível, SEM_DADO por motivo e episódios. Episódio: ciclos de alerta separados por menos de 3 h ficam no
 * mesmo episódio (pré-registro da validação de 2027, seção 2). Nunca compara com rótulo de alagamento.
 */
public final class ShadowWeeklyReportCalculator {

    public static final String RULE_VERSION = "sombra-semanal-v1";
    static final Duration CYCLE = Duration.ofMinutes(10);
    static final Duration GAP_LIMIT = Duration.ofMinutes(30);
    static final Duration EPISODE_SPLIT = Duration.ofHours(3);
    static final List<String> LEVELS = List.of("ALERTA", "ATENCAO", "SEM_ALERTA", "SEM_DADO");

    private ShadowWeeklyReportCalculator() {}

    /** Um ciclo gravado em flood_forecast_point. */
    public record Cycle(long pointId, Instant at, String level, String reason) {}

    public record Result(long pointId, String slug, int nCycles, int nExpected, int longestGapMin, int gapsOver30Min,
                         Map<String, Integer> byLevel, Map<String, Integer> semDadoByReason, int alertaEpisodes,
                         int alertaOuAtencaoEpisodes) {}

    /** points: id -> slug de todos os pontos ativos (ponto sem nenhum ciclo também sai, com o buraco da semana inteira). */
    public static List<Result> compute(Instant from, Instant to, Map<Long, String> points, List<Cycle> cycles) {
        int expected = (int) (Duration.between(from, to).toMinutes() / CYCLE.toMinutes());
        Map<Long, List<Cycle>> by = new TreeMap<>();
        for (Cycle c : cycles) {
            if (c.at().isBefore(from) || !c.at().isBefore(to)) continue;
            by.computeIfAbsent(c.pointId(), k -> new ArrayList<>()).add(c);
        }
        List<Result> out = new ArrayList<>();
        for (Map.Entry<Long, String> p : new TreeMap<>(points).entrySet()) {
            List<Cycle> cs = by.getOrDefault(p.getKey(), List.of()).stream().sorted(Comparator.comparing(Cycle::at)).toList();
            Map<String, Integer> levels = new LinkedHashMap<>();
            for (String l : LEVELS) levels.put(l, 0);
            Map<String, Integer> reasons = new TreeMap<>();
            Instant prev = from;
            long longest = 0;
            int over = 0;
            for (Cycle c : cs) {
                levels.merge(LEVELS.contains(c.level()) ? c.level() : "SEM_DADO", 1, Integer::sum);
                if (!LEVELS.contains(c.level()) || "SEM_DADO".equals(c.level())) {
                    reasons.merge(c.reason() == null ? "SEM_MOTIVO" : c.reason(), 1, Integer::sum);
                }
                Duration gap = Duration.between(prev, c.at());
                longest = Math.max(longest, gap.toMinutes());
                if (gap.compareTo(GAP_LIMIT) > 0) over++;
                prev = c.at();
            }
            Duration tail = Duration.between(prev, to);
            longest = Math.max(longest, tail.toMinutes());
            if (tail.compareTo(GAP_LIMIT) > 0) over++;
            out.add(new Result(p.getKey(), p.getValue(), cs.size(), expected, (int) longest, over, levels, reasons,
                    episodes(cs, List.of("ALERTA")), episodes(cs, List.of("ALERTA", "ATENCAO"))));
        }
        return out;
    }

    static int episodes(List<Cycle> sorted, List<String> levels) {
        int n = 0;
        Instant last = null;
        for (Cycle c : sorted) {
            if (!levels.contains(c.level())) continue;
            if (last == null || Duration.between(last, c.at()).compareTo(EPISODE_SPLIT) >= 0) n++;
            last = c.at();
        }
        return n;
    }
}
