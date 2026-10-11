package com.projeto.mapi.service.ingestion.tzverify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Teste B do F2-DADOS-07: causalidade e latência contra o relógio, para fontes que só existem ao vivo.
 *
 * <p>Uma leitura baixada em {@code collectedAt} (UTC, relógio do servidor) não pode ter instante de origem no
 * futuro, e telemetria em tempo quase real não chega com horas de atraso. Para cada hipótese {@code h}
 * ({@code rótulo = UTC + h}), {@code lag(h) = collectedAt − (rótulo − h)}. Hipóteses vizinhas diferem em 60 min,
 * então os dois limites juntos isolam uma única. Convenção de sinal igual à de {@link TzCorrelation}.
 */
public final class WallClockCheck {

    private WallClockCheck() {}

    /** Amostra: instante da coleta e rótulo "ingênuo" (segundos desde 1970 como se o rótulo fosse UTC). */
    public record Sample(String station, Instant collectedAt, long labelEpochSeconds) {}

    public record OffsetLag(int offsetHours, double p01Minutes, double medianMinutes, boolean accepted) {}

    public record Result(int nSamples, int nStations, List<OffsetLag> lags, List<Integer> acceptedOffsets,
                         boolean enoughPower, TzCorrelation.Verdict verdict, Integer offsetHours, String reason) {}

    /** Limiares fixados antes de calcular (relatório F2-DADOS-07, seção 1.2). */
    public record Criteria(double minP01Minutes, double minMedianMinutes, double maxMedianMinutes, int minSamples,
                           int minStations, Set<Integer> plausibleOffsets) {
        public static Criteria preRegistered() {
            return new Criteria(-10, 0, 30, 200, 20, Set.of(-3, 0));
        }
    }

    public static Result evaluate(List<Sample> samples, Criteria c) {
        int nStations = (int) samples.stream().map(Sample::station).distinct().count();
        List<OffsetLag> lags = new ArrayList<>();
        List<Integer> accepted = new ArrayList<>();
        for (int h = TzCorrelation.MIN_OFFSET; h <= TzCorrelation.MAX_OFFSET; h++) {
            double[] lag = new double[samples.size()];
            for (int i = 0; i < lag.length; i++) {
                Sample s = samples.get(i);
                long utc = s.labelEpochSeconds() - h * 3600L;
                lag[i] = (s.collectedAt().getEpochSecond() - utc) / 60.0;
            }
            Arrays.sort(lag);
            double p01 = lag.length == 0 ? Double.NaN : lag[(int) Math.floor(0.01 * (lag.length - 1))];
            double med = lag.length == 0 ? Double.NaN : quantile(lag, 0.5);
            boolean ok = lag.length > 0 && p01 >= c.minP01Minutes() && med >= c.minMedianMinutes() && med <= c.maxMedianMinutes();
            lags.add(new OffsetLag(h, p01, med, ok));
            if (ok) accepted.add(h);
        }
        boolean power = samples.size() >= c.minSamples() && nStations >= c.minStations();
        TzCorrelation.Verdict verdict;
        Integer offset = null;
        String reason;
        if (!power) {
            verdict = TzCorrelation.Verdict.INSUFFICIENT_DATA;
            reason = "amostras=" + samples.size() + " (min " + c.minSamples() + "), estações=" + nStations + " (min " + c.minStations() + ")";
        } else if (accepted.size() != 1) {
            verdict = TzCorrelation.Verdict.AMBIGUOUS;
            reason = "hipóteses aprovadas=" + accepted + " (esperada exatamente 1)";
        } else if (!c.plausibleOffsets().contains(accepted.get(0))) {
            verdict = TzCorrelation.Verdict.IMPLAUSIBLE_OFFSET;
            offset = accepted.get(0);
            reason = "única hipótese aprovada " + offset + " h fora de " + c.plausibleOffsets();
        } else {
            verdict = TzCorrelation.Verdict.ACCEPT;
            offset = accepted.get(0);
            reason = "única hipótese aprovada: " + offset + " h";
        }
        return new Result(samples.size(), nStations, lags, accepted, power, verdict, offset, reason);
    }

    private static double quantile(double[] sorted, double q) {
        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
    }
}
