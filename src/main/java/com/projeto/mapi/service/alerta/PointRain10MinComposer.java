package com.projeto.mapi.service.alerta;

import com.projeto.mapi.repository.analise.PointRain10MinRepository;
import com.projeto.mapi.repository.analise.PointRain10MinRepository.Block;
import com.projeto.mapi.util.GeoUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Série de chuva do ponto em passos de 10 min (item 1.4.1), com a mesma regra da série horária (ADR 0022):
 * maior valor entre as estações válidas a até 5 km; estação reprovada na checagem do mês fora; hora de pico isolado fora
 * (regra do {@code RainSpatialCheck}: 10 mm ou mais com pelo menos 2 vizinhas a até 6 km abaixo de 1 mm na mesma hora);
 * qualificação 0002 já fora (quarentena). Bloco longo (mais de 10 min) é espalhado por igual no seu intervalo e marcado.
 * Passo sem nenhuma estação próxima cobrindo fica nulo (ausente nunca vira zero). Lógica pura: sem Spring, sem banco.
 */
public final class PointRain10MinComposer {

    public static final int STEP_MIN = 10;
    public static final double NEAR_KM = 5.0;
    public static final double PEAK_MM = 10.0, PEAK_RADIUS_KM = 6.0, PEAK_NEIGHBOR_DRY_MM = 1.0;
    public static final int PEAK_MIN_NEIGHBORS = 2;
    /** Bloco maior que 60 min não tem tamanho confiável (a PED o põe em quarentena; ao vivo, é parada da coleta). */
    public static final int MAX_BLOCK_MIN = 60;

    /** Um passo de 10 min com fim em {@code end}. {@code mm} nulo = sem estação válida cobrindo. */
    public record Slot(Instant end, Double mm, String stationId, int nValid, boolean longBlock, Integer blockMinutes,
                       String source) {}

    /** Leitura descartada pelo controle de qualidade, com o motivo. */
    public record Dropped(String canonicalId, Instant ts, double mm, String reason) {}

    public record Result(List<Slot> slots, List<Dropped> dropped, Instant newestReading, Instant newestReceivedAt,
                         Instant newestIngestedAt) {}

    /** Filtro extra de qualidade ao vivo (item 2.4): devolve o motivo para descartar a leitura, ou null. */
    @FunctionalInterface
    public interface LiveFilter {
        String reject(Block b);

        LiveFilter NONE = b -> null;
    }

    private PointRain10MinComposer() {
    }

    static Instant ceilToStep(Instant t) {
        long s = t.getEpochSecond();
        long step = STEP_MIN * 60L;
        long r = Math.floorMod(s, step);
        return r == 0 ? Instant.ofEpochSecond(s) : Instant.ofEpochSecond(s - r + step);
    }

    static Instant hourEnd(Instant ts) {
        Instant floor = ts.truncatedTo(ChronoUnit.HOURS);
        return floor.equals(ts) ? ts : floor.plus(1, ChronoUnit.HOURS);
    }

    static Instant monthStart(Instant ts) {
        return ts.atOffset(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
    }

    /** Tamanho do bloco em minutos: início declarado (PED) ou leitura anterior (ao vivo). Nulo se desconhecido. */
    static Integer blockMinutes(Block b) {
        Instant start = b.intervalStart() != null && b.intervalStart().isBefore(b.ts()) ? b.intervalStart() : b.prevTs();
        if (start == null) return null;
        long m = Duration.between(start, b.ts()).toMinutes();
        return m <= 0 ? null : (int) m;
    }

    public static Result compose(List<Block> blocks, Map<String, String> monthStatus, Instant from, Instant to,
                                 LiveFilter liveFilter) {
        List<Dropped> dropped = new ArrayList<>();
        // 1. pico isolado, por hora e estação canônica (todas as estações carregadas servem de vizinhas)
        Map<String, Double> hourly = new HashMap<>();
        Map<String, double[]> pos = new HashMap<>();
        for (Block b : blocks) {
            hourly.merge(b.canonicalId() + "|" + hourEnd(b.ts()), b.mm(), Double::sum);
            pos.putIfAbsent(b.canonicalId(), new double[]{b.lat(), b.lon()});
        }
        Set<String> peaks = new HashSet<>();
        for (Map.Entry<String, Double> e : hourly.entrySet()) {
            if (e.getValue() < PEAK_MM) continue;
            String[] k = e.getKey().split("\\|");
            double[] p = pos.get(k[0]);
            int dry = 0;
            boolean wetNeighbor = false;
            for (Map.Entry<String, double[]> n : pos.entrySet()) {
                if (n.getKey().equals(k[0])) continue;
                Double v = hourly.get(n.getKey() + "|" + k[1]);
                if (v == null) continue;
                if (GeoUtils.calculateDistance(p[0], p[1], n.getValue()[0], n.getValue()[1]) > PEAK_RADIUS_KM) continue;
                if (v >= PEAK_NEIGHBOR_DRY_MM) {
                    wetNeighbor = true;
                    break;
                }
                dry++;
            }
            if (!wetNeighbor && dry >= PEAK_MIN_NEIGHBORS) peaks.add(e.getKey());
        }
        // 2. espalha cada bloco válido nos passos de 10 min
        Map<Instant, Map<String, double[]>> bySlot = new HashMap<>();   // slot -> estação -> {mm, longo, minutos}
        Instant newest = null, newestRecv = null, newestIng = null;
        for (Block b : blocks) {
            if (b.km() > NEAR_KM) continue;
            String reason = null;
            if (peaks.contains(b.canonicalId() + "|" + hourEnd(b.ts()))) reason = "PICO_ISOLADO";
            else if ("FAIL".equals(monthStatus.get(PointRain10MinRepository.key(b.canonicalId(), monthStart(b.ts())))))
                reason = "ESTACAO_REPROVADA_NO_MES";
            else if (liveFilter != null) reason = liveFilter.reject(b);
            Integer len = blockMinutes(b);
            if (reason == null && (len == null || len > MAX_BLOCK_MIN)) reason = "BLOCO_SEM_TAMANHO";
            if (reason != null) {
                if (!"BLOCO_SEM_TAMANHO".equals(reason) || b.mm() > 0) dropped.add(new Dropped(b.canonicalId(), b.ts(), b.mm(), reason));
                continue;
            }
            if (newest == null || b.ts().isAfter(newest)) {
                newest = b.ts();
                newestRecv = b.receivedAt();
                newestIng = b.ingestedAt();
            }
            Instant end = ceilToStep(b.ts());
            int n = Math.max(1, (int) Math.ceil(len / (double) STEP_MIN));
            double per = b.mm() / n;
            for (int k = 0; k < n; k++) {
                Instant s = end.minus((long) k * STEP_MIN, ChronoUnit.MINUTES);
                bySlot.computeIfAbsent(s, x -> new HashMap<>())
                        .merge(b.canonicalId(), new double[]{per, len > STEP_MIN ? 1 : 0, len}, (a, c) -> new double[]{a[0] + c[0],
                                Math.max(a[1], c[1]), Math.max(a[2], c[2])});
            }
        }
        // 3. passo a passo: maior valor entre as estações próximas que cobrem o passo
        List<Slot> slots = new ArrayList<>();
        for (Instant s = ceilToStep(from.plusSeconds(1)); !s.isAfter(to); s = s.plus(STEP_MIN, ChronoUnit.MINUTES)) {
            Map<String, double[]> st = bySlot.get(s);
            if (st == null || st.isEmpty()) {
                slots.add(new Slot(s, null, null, 0, false, null, null));
                continue;
            }
            String best = null;
            double[] bv = null;
            for (Map.Entry<String, double[]> e : st.entrySet()) {
                if (bv == null || e.getValue()[0] > bv[0] || (e.getValue()[0] == bv[0] && e.getKey().compareTo(best) < 0)) {
                    best = e.getKey();
                    bv = e.getValue();
                }
            }
            slots.add(new Slot(s, round(bv[0]), best, st.size(), bv[1] > 0, (int) bv[2], null));
        }
        return new Result(slots, dropped, newest, newestRecv, newestIng);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
