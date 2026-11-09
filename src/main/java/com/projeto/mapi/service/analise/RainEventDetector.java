package com.projeto.mapi.service.analise;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Detecção de eventos de chuva sobre a grade horária (ADR 0017, {@code docs/EVENTOS-DE-CHUVA.md}). Lógica pura, sem
 * framework: entra uma série horária contígua, saem os eventos.
 *
 * <p>Regras (todas em UTC; a hora é rotulada pelo FIM do intervalo {@code (t-1h, t]}):
 * <ul>
 *   <li>Hora molhada: valor &gt; 0. Hora seca: valor = 0 medido. Hora sem valor (incompleta ou sem leitura): lacuna,
 *       nunca zero.</li>
 *   <li>Um evento começa numa hora molhada e termina na última hora molhada antes de uma sequência de
 *       {@code dryGapHours} horas não molhadas (secas ou lacuna). Duas horas molhadas separadas por menos que isso
 *       ficam no mesmo evento.</li>
 *   <li>Lacuna nunca prolonga nem separa sozinha um evento com certeza: lacuna dentro do evento, nas
 *       {@code dryGapHours} horas depois do fim ou nas {@code dryGapHours} horas antes do início marca {@code hasGap}.
 *       </li>
 *   <li>Evento com total abaixo de {@code minEventMm} é descartado (ruído); as horas dele contam como intervalo entre
 *       os eventos vizinhos.</li>
 *   <li>{@code dryHoursBefore}: horas entre o fim do evento retido anterior e o início deste; nulo se não há evento
 *       anterior na série ou se há lacuna entre os dois (não se sabe se houve evento no meio).</li>
 *   <li>Evento que encosta no início ou no fim da série (sem as horas de separação completas) sai com
 *       {@code touchesSeriesEdge = true}; quem chama marca como truncado.</li>
 * </ul>
 */
public final class RainEventDetector {

    private static final Duration HOUR = Duration.ofHours(1);

    private RainEventDetector() {
    }

    /** Uma hora da grade; {@code mm} nulo = lacuna. {@code hourEnd} é o fim do intervalo, em UTC. */
    public record HourSample(Instant hourEnd, Double mm, int received, int expected, List<UUID> runIds) {
        public HourSample {
            runIds = runIds == null ? List.of() : List.copyOf(runIds);
        }

        boolean wet() { return mm != null && mm > 0; }

        boolean missing() { return mm == null; }
    }

    /** Regras de segmentação. {@code capacityMmH} entra só no cálculo de sobrecarga (não muda o evento). */
    public record Rules(int dryGapHours, double minEventMm, double capacityMmH) {
        public Rules {
            if (dryGapHours < 1) throw new IllegalArgumentException("dryGapHours deve ser >= 1");
            if (minEventMm < 0) throw new IllegalArgumentException("minEventMm deve ser >= 0");
            if (capacityMmH <= 0) throw new IllegalArgumentException("capacityMmH deve ser > 0");
        }
    }

    /** Evento detectado; índices referem-se à série de entrada. */
    public record DetectedEvent(int firstIndex, int lastIndex, Instant startUtc, Instant endUtc, int durationMin,
                                double totalMm, double meanIntensityMmH, double maxHourlyMmH, int peakOffsetMin,
                                Double dryHoursBefore, int expectedReadings, int receivedReadings, boolean hasGap,
                                boolean touchesSeriesEdge, Set<UUID> runIds, int hoursAboveCapacity,
                                double excessOverCapacityMm) {}

    public static List<DetectedEvent> detect(List<HourSample> series, Rules rules) {
        checkContiguous(series);
        int n = series.size();
        List<int[]> spans = new ArrayList<>(); // [first, last, gapInside(0/1), closedBySeriesEnd(0/1)]
        int i = 0;
        while (i < n) {
            if (!series.get(i).wet()) { i++; continue; }
            int first = i, last = i;
            boolean gapInside = false, runHasMissing = false;
            int nonWetRun = 0;
            int k = i + 1;
            for (; k < n; k++) {
                HourSample h = series.get(k);
                if (h.wet()) {
                    if (runHasMissing) gapInside = true;
                    last = k;
                    nonWetRun = 0;
                    runHasMissing = false;
                } else {
                    nonWetRun++;
                    if (h.missing()) runHasMissing = true;
                    if (nonWetRun >= rules.dryGapHours()) break;
                }
            }
            boolean closedBySeriesEnd = k >= n; // a série acabou antes de completar a separação
            spans.add(new int[]{first, last, gapInside ? 1 : 0, closedBySeriesEnd ? 1 : 0});
            i = last + 1;
        }

        List<DetectedEvent> out = new ArrayList<>();
        DetectedEvent previous = null;
        for (int[] s : spans) {
            DetectedEvent e = build(series, rules, s[0], s[1], s[2] == 1, s[3] == 1, previous);
            if (e.totalMm() < rules.minEventMm()) continue;
            out.add(e);
            previous = e;
        }
        return out;
    }

    private static DetectedEvent build(List<HourSample> series, Rules rules, int first, int last, boolean gapInside,
                                       boolean closedBySeriesEnd, DetectedEvent previous) {
        int g = rules.dryGapHours();
        double total = 0, max = -1;
        int peak = first, expected = 0, received = 0, above = 0;
        double excess = 0;
        Set<UUID> runs = new TreeSet<>();
        for (int k = first; k <= last; k++) {
            HourSample h = series.get(k);
            expected += h.expected();
            received += h.received();
            runs.addAll(h.runIds());
            if (h.mm() == null) continue;
            total += h.mm();
            if (h.mm() > max) { max = h.mm(); peak = k; }
            if (h.mm() > rules.capacityMmH()) {
                above++;
                excess += h.mm() - rules.capacityMmH();
            }
        }
        boolean gapAround = anyMissing(series, Math.max(0, first - g), first - 1)
                || anyMissing(series, last + 1, Math.min(series.size() - 1, last + g));
        boolean touchesEdge = first < g || closedBySeriesEnd;

        Double dryBefore = null;
        if (previous != null && !anyMissing(series, previous.lastIndex() + 1, first - 1)) {
            dryBefore = (double) (first - 1 - previous.lastIndex());
        }

        Instant start = series.get(first).hourEnd().minus(HOUR);
        Instant end = series.get(last).hourEnd();
        int hours = last - first + 1;
        return new DetectedEvent(first, last, start, end, hours * 60, total, total / hours, Math.max(max, 0),
                (peak - first) * 60, dryBefore, expected, received, gapInside || gapAround, touchesEdge,
                runs, above, excess);
    }

    private static boolean anyMissing(List<HourSample> series, int from, int to) {
        for (int k = Math.max(from, 0); k <= to && k < series.size(); k++) if (series.get(k).missing()) return true;
        return false;
    }

    private static void checkContiguous(List<HourSample> series) {
        for (int k = 1; k < series.size(); k++) {
            if (!series.get(k).hourEnd().equals(series.get(k - 1).hourEnd().plus(HOUR))) {
                throw new IllegalArgumentException("série horária não contígua em " + series.get(k).hourEnd());
            }
        }
    }
}
