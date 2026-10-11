package com.projeto.mapi.service.ingestion.tzverify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Verificação do fuso de uma série de chuva por correlação cruzada com uma referência em UTC (F2-DADOS-07).
 *
 * <p>Convenção de sinal: {@code offsetHours = h} significa que o rótulo da fonte é a hora local de um fuso UTC+h
 * ({@code rótulo = UTC + h}); logo {@code UTC = rótulo - h}. Dado em UTC−3 corresponde a {@code h = -3}.
 *
 * <p>Para cada hipótese {@code h} em {@code [-6, +6]}: (1) os registros sub-horários da estação são deslocados
 * para UTC e somados em hora rotulada pelo FIM do intervalo {@code (H-1h, H]} (mesma convenção do Open-Meteo e do
 * ADR 0006 seção 5); (2) a hora só vale se tiver ao menos {@code minReadings} leituras; (3) calculam-se Pearson e
 * Spearman nas horas comuns com a referência; (4) o IC 95 % vem de bootstrap por blocos de dias UTC (os mesmos
 * dias sorteados valem para todas as hipóteses, o que permite medir a estabilidade do argmax).
 *
 * <p>Tudo aqui é puro (sem I/O, sem relógio, sem fuso da JVM).
 */
public final class TzCorrelation {

    public static final int MIN_OFFSET = -6;
    public static final int MAX_OFFSET = 6;
    /** Chuva horária da referência a partir da qual a hora conta como "com chuva". */
    public static final double WET_THRESHOLD_MM = 0.1;

    private TzCorrelation() {}

    /** Leitura de chuva da estação: rótulo interpretado como relógio de parede "ingênuo" (segundos desde 1970 como se fosse UTC). */
    public record Reading(long labelEpochSeconds, double mm) {}

    /** Resultado de uma hipótese de deslocamento. */
    public record OffsetResult(int offsetHours, int nHours, int nWetHours, int nWetDays, double pearson, double spearman,
                               double pearsonLo, double pearsonHi, double spearmanLo, double spearmanHi) {}

    /** Resultado completo: todas as hipóteses mais as medidas de estabilidade do argmax por bootstrap. */
    public record Evaluation(List<OffsetResult> results, int bestOffset, int secondOffset, double margin,
                             double bootstrapBestBeatsSecond, double bootstrapBestIsArgmax, int bootstrapReplicates) {
        public OffsetResult of(int offset) {
            return results.stream().filter(r -> r.offsetHours() == offset).findFirst().orElseThrow();
        }
    }

    /**
     * Soma as leituras em horas UTC rotuladas pelo fim do intervalo. A chave é a hora (segundos UTC / 3600) do FIM.
     * Hora com menos de {@code minReadings} leituras fica de fora (ausência, nunca zero).
     */
    public static Map<Long, Double> aggregateHourly(List<Reading> readings, int offsetHours, int minReadings) {
        Map<Long, double[]> acc = new HashMap<>(); // [soma, contagem]
        for (Reading r : readings) {
            long utc = r.labelEpochSeconds() - offsetHours * 3600L;
            long hourEnd = Math.floorDiv(utc + 3599, 3600); // ceil: instante exato na hora pertence a (H-1, H]
            double[] a = acc.computeIfAbsent(hourEnd, k -> new double[2]);
            a[0] += r.mm();
            a[1] += 1;
        }
        Map<Long, Double> out = new TreeMap<>();
        acc.forEach((h, a) -> {
            if (a[1] >= minReadings) out.put(h, a[0]);
        });
        return out;
    }

    /**
     * Avalia as 13 hipóteses. {@code reference} é a chuva horária em UTC, chave = hora do FIM do intervalo.
     *
     * @param bootstrap número de réplicas (0 desliga o IC; os intervalos saem NaN)
     */
    public static Evaluation evaluate(List<Reading> station, Map<Long, Double> reference, int minReadings,
                                      int bootstrap, long seed) {
        int nOff = MAX_OFFSET - MIN_OFFSET + 1;
        // universo de dias: dias UTC da referência
        long minDay = Long.MAX_VALUE, maxDay = Long.MIN_VALUE;
        for (Long h : reference.keySet()) {
            long d = Math.floorDiv(h, 24L);
            minDay = Math.min(minDay, d);
            maxDay = Math.max(maxDay, d);
        }
        int nDays = reference.isEmpty() ? 0 : (int) (maxDay - minDay + 1);

        double[][][] rawM = new double[nOff][][];   // [offset][dia][6 momentos]
        double[][][] rankM = new double[nOff][][];
        int[] nHours = new int[nOff];
        int[] nWet = new int[nOff];
        int[] nWetDays = new int[nOff];
        double[] pear = new double[nOff];
        double[] spear = new double[nOff];

        for (int k = 0; k < nOff; k++) {
            int h = MIN_OFFSET + k;
            Map<Long, Double> agg = aggregateHourly(station, h, minReadings);
            List<long[]> keys = new ArrayList<>();
            List<Double> xs = new ArrayList<>();
            List<Double> ys = new ArrayList<>();
            for (Map.Entry<Long, Double> e : agg.entrySet()) {
                Double y = reference.get(e.getKey());
                if (y == null || y.isNaN()) continue;
                keys.add(new long[]{e.getKey()});
                xs.add(e.getValue());
                ys.add(y);
            }
            int n = xs.size();
            nHours[k] = n;
            double[] x = xs.stream().mapToDouble(Double::doubleValue).toArray();
            double[] y = ys.stream().mapToDouble(Double::doubleValue).toArray();
            double[] rx = ranks(x);
            double[] ry = ranks(y);
            rawM[k] = new double[nDays][6];
            rankM[k] = new double[nDays][6];
            Set<Long> wetDays = new java.util.HashSet<>();
            for (int i = 0; i < n; i++) {
                long hourKey = keys.get(i)[0];
                int di = (int) (Math.floorDiv(hourKey, 24L) - minDay);
                add(rawM[k][di], x[i], y[i]);
                add(rankM[k][di], rx[i], ry[i]);
                if (y[i] >= WET_THRESHOLD_MM) {
                    nWet[k]++;
                    wetDays.add(Math.floorDiv(hourKey, 24L));
                }
            }
            nWetDays[k] = wetDays.size();
            pear[k] = corr(sum(rawM[k]));
            spear[k] = corr(sum(rankM[k]));
        }

        double[][] pearBoot = new double[nOff][];
        double[][] spearBoot = new double[nOff][];
        int bestPoint = argmax(spear);
        int secondPoint = secondArgmax(spear, bestPoint);
        int beats = 0, isArg = 0, reps = 0;
        if (bootstrap > 0 && nDays > 1) {
            Random rnd = new Random(seed);
            for (int k = 0; k < nOff; k++) {
                pearBoot[k] = new double[bootstrap];
                spearBoot[k] = new double[bootstrap];
            }
            int[] pick = new int[nDays];
            for (int b = 0; b < bootstrap; b++) {
                for (int i = 0; i < nDays; i++) pick[i] = rnd.nextInt(nDays);
                double[] sp = new double[nOff];
                for (int k = 0; k < nOff; k++) {
                    pearBoot[k][b] = corr(sumPicked(rawM[k], pick));
                    sp[k] = corr(sumPicked(rankM[k], pick));
                    spearBoot[k][b] = sp[k];
                }
                reps++;
                if (gt(sp[bestPoint], sp[secondPoint])) beats++;
                if (argmax(sp) == bestPoint) isArg++;
            }
        }

        List<OffsetResult> res = new ArrayList<>();
        for (int k = 0; k < nOff; k++) {
            double[] pci = ci(pearBoot[k]);
            double[] sci = ci(spearBoot[k]);
            res.add(new OffsetResult(MIN_OFFSET + k, nHours[k], nWet[k], nWetDays[k], pear[k], spear[k],
                    pci[0], pci[1], sci[0], sci[1]));
        }
        double margin = spear[bestPoint] - spear[secondPoint];
        return new Evaluation(res, MIN_OFFSET + bestPoint, MIN_OFFSET + secondPoint, margin,
                reps == 0 ? Double.NaN : (double) beats / reps, reps == 0 ? Double.NaN : (double) isArg / reps, reps);
    }

    // ---- decisão ------------------------------------------------------------------------------

    /** Critério de decisão, fixado antes de calcular (ver relatório F2-DADOS-07). */
    public record Criteria(int minWetHours, int minWetDays, double minRho, double minMargin, double minStability,
                           Set<Integer> plausibleOffsets) {
        public static Criteria preRegistered() {
            return new Criteria(50, 5, 0.30, 0.05, 0.95, Set.of(-3, 0));
        }
    }

    public enum Verdict { ACCEPT, INSUFFICIENT_DATA, WEAK_CORRELATION, AMBIGUOUS, IMPLAUSIBLE_OFFSET }

    public record Decision(Verdict verdict, Integer offsetHours, String reason) {}

    /**
     * Decisão por estação. ACCEPT exige: poder suficiente (horas e dias com chuva na referência); argmax do Spearman
     * em um deslocamento plausível; Spearman do vencedor &gt;= minRho; margem sobre o segundo &gt;= minMargin; e o vencedor
     * superar o segundo em &gt;= minStability das réplicas do bootstrap. Qualquer outra coisa mantém a quarentena.
     */
    public static Decision decide(Evaluation ev, Criteria c) {
        OffsetResult best = ev.of(ev.bestOffset());
        if (best.nWetHours() < c.minWetHours() || best.nWetDays() < c.minWetDays()) {
            return new Decision(Verdict.INSUFFICIENT_DATA, null, "horas com chuva=" + best.nWetHours() + " (min "
                    + c.minWetHours() + "), dias com chuva=" + best.nWetDays() + " (min " + c.minWetDays() + ")");
        }
        if (Double.isNaN(best.spearman()) || best.spearman() < c.minRho()) {
            return new Decision(Verdict.WEAK_CORRELATION, null, "spearman máximo=" + fmt(best.spearman()) + " < " + c.minRho());
        }
        if (!c.plausibleOffsets().contains(ev.bestOffset())) {
            return new Decision(Verdict.IMPLAUSIBLE_OFFSET, ev.bestOffset(), "argmax em " + ev.bestOffset()
                    + " h, fora de " + c.plausibleOffsets());
        }
        if (ev.margin() < c.minMargin() || Double.isNaN(ev.bootstrapBestBeatsSecond())
                || ev.bootstrapBestBeatsSecond() < c.minStability()) {
            return new Decision(Verdict.AMBIGUOUS, ev.bestOffset(), "margem=" + fmt(ev.margin()) + " (min " + c.minMargin()
                    + "), estabilidade=" + fmt(ev.bootstrapBestBeatsSecond()) + " (min " + c.minStability() + ")");
        }
        return new Decision(Verdict.ACCEPT, ev.bestOffset(), "argmax=" + ev.bestOffset() + " h, spearman=" + fmt(best.spearman())
                + ", margem=" + fmt(ev.margin()) + ", estabilidade=" + fmt(ev.bootstrapBestBeatsSecond()));
    }

    /**
     * Decisão da fonte: pelo menos {@code minStations} estações com ACCEPT, todas no mesmo deslocamento, e
     * nenhuma estação com poder suficiente que indique outro deslocamento (IMPLAUSIBLE_OFFSET ou ACCEPT diferente).
     */
    public static Decision decideSource(List<Decision> stations, int minStations) {
        long accepted = stations.stream().filter(d -> d.verdict() == Verdict.ACCEPT).count();
        var offsets = stations.stream().filter(d -> d.verdict() == Verdict.ACCEPT).map(Decision::offsetHours).distinct().toList();
        long contrary = stations.stream().filter(d -> d.verdict() == Verdict.IMPLAUSIBLE_OFFSET).count();
        if (accepted < minStations) {
            return new Decision(Verdict.INSUFFICIENT_DATA, null, "estações aceitas=" + accepted + " (min " + minStations + ")");
        }
        if (offsets.size() != 1 || contrary > 0) {
            return new Decision(Verdict.AMBIGUOUS, null, "estações divergentes: offsets aceitos=" + offsets + ", contrárias=" + contrary);
        }
        return new Decision(Verdict.ACCEPT, offsets.get(0), accepted + " estações no mesmo deslocamento " + offsets.get(0) + " h");
    }

    // ---- estatística --------------------------------------------------------------------------

    /** Postos com média dos empates. */
    static double[] ranks(double[] v) {
        int n = v.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> v[i]));
        double[] r = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && v[idx[j + 1]] == v[idx[i]]) j++;
            double avg = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) r[idx[k]] = avg;
            i = j + 1;
        }
        return r;
    }

    private static void add(double[] m, double x, double y) {
        m[0] += 1;
        m[1] += x;
        m[2] += y;
        m[3] += x * x;
        m[4] += y * y;
        m[5] += x * y;
    }

    private static double[] sum(double[][] days) {
        double[] s = new double[6];
        for (double[] d : days) for (int i = 0; i < 6; i++) s[i] += d[i];
        return s;
    }

    private static double[] sumPicked(double[][] days, int[] pick) {
        double[] s = new double[6];
        for (int p : pick) {
            double[] d = days[p];
            for (int i = 0; i < 6; i++) s[i] += d[i];
        }
        return s;
    }

    private static double corr(double[] m) {
        double n = m[0];
        if (n < 3) return Double.NaN;
        double cov = m[5] - m[1] * m[2] / n;
        double vx = m[3] - m[1] * m[1] / n;
        double vy = m[4] - m[2] * m[2] / n;
        if (vx <= 1e-12 || vy <= 1e-12) return Double.NaN;
        return cov / Math.sqrt(vx * vy);
    }

    private static boolean gt(double a, double b) {
        return !Double.isNaN(a) && (Double.isNaN(b) || a > b);
    }

    private static int argmax(double[] v) {
        int best = 0;
        for (int i = 1; i < v.length; i++) if (gt(v[i], v[best])) best = i;
        return best;
    }

    private static int secondArgmax(double[] v, int best) {
        int second = best == 0 ? 1 : 0;
        for (int i = 0; i < v.length; i++) if (i != best && gt(v[i], v[second])) second = i;
        return second;
    }

    private static double[] ci(double[] boot) {
        if (boot == null) return new double[]{Double.NaN, Double.NaN};
        double[] s = Arrays.stream(boot).filter(d -> !Double.isNaN(d)).sorted().toArray();
        if (s.length < 20) return new double[]{Double.NaN, Double.NaN};
        return new double[]{s[(int) Math.floor(0.025 * (s.length - 1))], s[(int) Math.ceil(0.975 * (s.length - 1))]};
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.3f", d);
    }
}
